package li_ke.rxjavastudy.rxJava.study6

import rx.Observable
import rx.Subscriber
import rx.exceptions.Exceptions
import rx.functions.Action0
import rx.functions.Action1
import rx.functions.Func1
import rx.plugins.RxJavaHooks


/**
 * 先看 [Study6]
 * 说的上一层下一层，指的是：RxJava 操作运算符的 上一个运算符，下一个运算符。
 * .create{ ... } // subscribe 的上一层
 * .subscribe{ ... } // create 的下一层
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

    fun unsafeSubscribe(subscriber: Subscriber<T>?) {
        try {
            this.onSubscribe.call(subscriber)
        } catch (e: Exception) {
            //与SafeSubscriber相同的异常处理逻辑： 代码异常 -> onError() ， RxJava使用异常 -> 抛出崩溃
        }
    }

    fun <R> map(mapBlock: Func1<T, R>): MyObservable<R> {
        return MyObservable(MyOnSubscribeMap(this, mapBlock))
    }

    // 泛型 T -> R 当前类型为 T 转换为 R ，要注意 Operator 的泛型是 反着声明的，在其注解中也有提到。
    // 传入 Operator 的 Subscriber 是下层 call() 中封装好的 Subscriber，类型是下层的 R 类型
    // Operator.call() 得到的 Subscriber 是要传给上层的 parent.call() 是内部创建的 Subscriber ，类型是 T
    fun <R> lift(operator: Observable.Operator<R, T>): MyObservable<R> {
        return create(MyOnSubscribeLift(this.onSubscribe, operator))
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
    override fun call(subscriber: Subscriber<in R>?) {
        val parent = MyMapSubscriber(subscriber, transformer)
        source.unsafeSubscribe(parent)
    }
}

/** 抽象化（Subscriber 封装）*/ // T：当前类型  R：转换后类型 = 上层 Observable.onSubscribe 类型
class MyOnSubscribeLift<T, R>(val parent: Observable.OnSubscribe<R>, val operator: Observable.Operator<T, R>) : Observable.OnSubscribe<T> {
    override fun call(t: Subscriber<in T>?) {
        val newSubscriber = operator.call(t) // 类型转换 T -> R
        parent.call(newSubscriber)
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
                println("onSubscribe")
                it.onNext(5)
            }).lift<String>(Observable.Operator { subscriber ->
                // subscriber = 下层 call() 中封装好的 SafeSubscriber<String>
                println("lift")
                return@Operator object : Subscriber<Int>() { // 上层要拿走 call() 的 Subscriber<Int>
                    override fun onNext(t: Int?) {
                        println("lift-onNext")
                        subscriber.onNext("subscriber")
                    }

                    override fun onCompleted() {
                        subscriber.onCompleted()
                    }

                    override fun onError(e: Throwable?) {
                        subscriber.onError(e)
                    }
                }
            }).subscribe(Action1 { t ->
                println(t)
            })
}