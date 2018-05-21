package li_ke.rxjavastudy.rxJava.study4

import rx.Observable
import rx.Subscriber
import rx.exceptions.Exceptions
import rx.functions.Action0
import rx.functions.Action1
import rx.plugins.RxJavaHooks

/**
 * 先看 [Study4]
 */
class MyObservable<T> private constructor(val onSubscribe: Observable.OnSubscribe<T>) {
    //2、
    fun subscribe(f: Action1<T>) {

        var action: Subscriber<T> = MyActionSubscriber(f,
                Action1 { throwable -> throw throwable },
                Action0 {})

        action = MySafeSubscriber(action)

        try {
            this.onSubscribe.call(action)
        } catch (e: Exception) {
            //与SafeSubscriber相同的异常处理逻辑： 代码异常 -> onError() ， RxJava使用异常 -> 抛出崩溃
        }
    }

    companion object {
        //1、创建 Observable 并保存了 onSubscribe
        fun <T> create(onSubscribe: Observable.OnSubscribe<T>): MyObservable<T> {
            return MyObservable(onSubscribe)
        }

        fun <T> create(array: Array<T>): MyObservable<T> {
            return MyObservable(MyOnSubscribeFromArray(array))
        }
    }
}

// 扩展 call()
class MyOnSubscribeFromArray<T>(val array: Array<T>) : Observable.OnSubscribe<T> {
    var child: Subscriber<in T>? = null
    //3、
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

//将单个的 Action1 扩展为 有 onError 与 onCompleted 的 Subscriber
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

//增加对异常的处理
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

fun useDemo() {
    MyObservable.create(arrayOf(1, 2, 3, 4))
            .subscribe(Action1 { t ->
                println(t)
            })
}