package li_ke.rxjavastudy.rxJava.study5

import rx.Observable
import rx.Subscriber
import rx.exceptions.Exceptions
import rx.functions.Action0
import rx.functions.Action1
import rx.functions.Func1
import rx.plugins.RxJavaHooks

/**
 * 先看 [Study5]
 */
class MyObservable<T> private constructor(val onSubscribe: Observable.OnSubscribe<T>) {

    fun subscribe(f: Action1<T>) {

        var action: Subscriber<T> = MyActionSubscriber(f,
                Action1 { throwable -> throw throwable },
                Action0 {})

        if (action !is MySafeSubscriber)
            action = MySafeSubscriber(action)

        try {
            this.onSubscribe.call(action)
        } catch (e: Exception) {
            //与SafeSubscriber相同的异常处理逻辑： 代码异常 -> onError() ， RxJava使用异常 -> 抛出崩溃
        }
    }

    // 1、调用map
    fun <R> map(mapBlock: Func1<T, R>): MyObservable<R> {
        // 2、将 mapBlock 与 旧的 Observable 封装
        return MyObservable(MyOnSubscribeMap(this, mapBlock))
    }

    // 4、Observable : 一切交给 onSubscribe 处理
    fun unsafeSubscribe(subscriber: Subscriber<T>?) {
        onSubscribe.call(subscriber)
    }

    companion object {

        fun <T> create(onSubscribe: Observable.OnSubscribe<T>): MyObservable<T> {
            return MyObservable(onSubscribe)
        }

        fun <T> create(array: Array<T>): MyObservable<T> {
            return MyObservable(MyOnSubscribeFromArray(array))
        }
    }
}

///////////////////////////////////////////////////////////////////////////
// OnSubscribe 的扩展
///////////////////////////////////////////////////////////////////////////

/** 数组型 OnSubscribe */
class MyOnSubscribeFromArray<T>(val array: Array<T>) : Observable.OnSubscribe<T> {
    var child: Subscriber<in T>? = null

    override fun call(t: Subscriber<in T>?) {
        child = t
        fastPath()
    }

    //实际执行我们的代码
    private fun fastPath() {
        val child = this.child // child == SafeSubscriber
        for (t in array) {
            if (child?.isUnsubscribed == true) { // 检测有效性
                return
            }

            child?.onNext(t) // 执行我们的代码
        }

        if (child?.isUnsubscribed == true) {
            return
        }
        child?.onCompleted() // 最后走一遍 onCompleted()
    }

    //private fun slowPath() // 被我们忽略的方法，与线程有关
}

/** 类型转换 T -> R */ // 封装 T 类型的 Observable，自身却是 R 类型，由此进行转换。 call() 时实际走的也是 R 类型
class MyOnSubscribeMap<T, R>(val source: MyObservable<T>, val transformer: Func1<T, R>) : Observable.OnSubscribe<R> {
    // 3、 OnSubscribeMap 的 call 会在 Observable.subscribe 时调用
    // 会从 新的 OnSubscribeMap 到 旧的 OnSubscribeMap 逐个执行 进行封装 subscriber 操作
    // 这样会导致 MapSubscriber 的引用树中，上层是 旧的 mapBlock，下层是 新的 mapBlock。 对象层的递归?
    override fun call(subscriber: Subscriber<in R>?) {
        val parent = MyMapSubscriber(subscriber, transformer) // 先交给老子处理，再交给小的处理
        source.unsafeSubscribe(parent)
    }
}

///////////////////////////////////////////////////////////////////////////
// Subscriber 的扩展
///////////////////////////////////////////////////////////////////////////

/** 扩展 onError 与 onCompleted */
class MyActionSubscriber<T>(val onNext: Action1<in T>, val onError: Action1<Throwable>, val onCompleted: Action0) : Subscriber<T>() {

    override fun onNext(t: T) {
        onNext.call(t)
    }

    override fun onError(e: Throwable) {
        onError.call(e)
    }

    override fun onCompleted() {
        onCompleted.call()
    }
}

/** 增加对异常的处理*/
class MySafeSubscriber<T>(val actual: Subscriber<in T>) : Subscriber<T>(actual, true) {
    var done: Boolean = false

    override fun onNext(t: T) {
        try {
            if (!done)
                actual.onNext(t)
        } catch (e: Throwable) {
            Exceptions.throwIfFatal(e)
            onError(e)
        }
    }

    override fun onCompleted() {
        if (!done) {
            done = true
            try {
                actual.onCompleted()
            } catch (e: Throwable) {
                Exceptions.throwIfFatal(e)
                try {
                    unsubscribe()
                } catch (e: Throwable) {
                    RxJavaHooks.onError(e)
                }
            }
        }
    }

    //若异常在 Exceptions.throwIfFatal(e) 中抛出，即 RxJava 操作异常会崩溃
    override fun onError(e: Throwable?) {
        Exceptions.throwIfFatal(e)
        if (!done) {
            done = true
            try {
                actual.onError(e)
            } catch (e: Throwable) {
                try {
                    unsubscribe()
                } catch (e: Throwable) {
                    RxJavaHooks.onError(e)
                }
            }
        }
    }
}

/** 由 OnSubscriberMap 创建，用于正向执行 mapBlock */ // T -> R 自身是 T 类型，所以 onNext() 传入的是 T ，但内部的 subscriber 是 R 类型，所以到下一层就传的是 R 类型
class MyMapSubscriber<T, R>(val actual: Subscriber<in R>?, val mapper: Func1<T, R>) : Subscriber<T>() {

    // 4、onNext() 时 MapSubscriber 的引用树已经是：上层 旧的 mapBlock 即 mapBlock1。 下层 新的 mapBlock 即 mapBlock2
    override fun onNext(t: T) {
        val result = mapper.call(t)
        actual?.onNext(result)
    }

    override fun onCompleted() {
        actual?.onCompleted()
    }

    override fun onError(e: Throwable?) {
        actual?.onError(e)
    }
}

fun useDemo() {
    MyObservable
            .create<Int>(Observable.OnSubscribe {
                it.onNext(2)
            }).map<Int>(Func1 {
                it * it
            }).map<String>(Func1 {
                "$it"
            }).subscribe(Action1 { t ->
                println(t)
            })
}