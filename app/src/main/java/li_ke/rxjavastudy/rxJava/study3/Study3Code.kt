package li_ke.rxjavastudy.rxJava.study3

import rx.Observable
import rx.Subscriber
import rx.exceptions.Exceptions
import rx.functions.Action0
import rx.functions.Action1
import rx.plugins.RxJavaHooks

/**
 * 先看 [Study3]
 */
class MyObservable<T> private constructor(val onSubscribe: Observable.OnSubscribe<T>) {
    //2、
    fun subscribe(f: Action1<T>) {
        //对Action1 使用了装饰模式 —— 在不改变结构(接口)的情况下，增强/扩展功能。

        //对 Action1 的第一层包装，扩展了onError()与onCompleted()两个方法
        var action: Subscriber<T> = MyActionSubscriber(f,// onSubscribe 中 onNext 时执行 f.onNext
                Action1 { throwable -> throw throwable },// onSubscribe 中 onError 时执行异常抛出
                Action0 {})// onSubscribe 中 onCompleted 时执行无操作

        //对 Action1 的第二层包装，扩展了对异常的处理
        action = MySafeSubscriber(action)

        //传给我们的 onSubscribe 让我们去调 onNext
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
    }
}

//ActionSubscriber 装饰的意义是 将单个的 Action1 扩展为 有 onError 与 onCompleted 的 Subscriber
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

//SafeSubscriber 装饰的意义是 增加对异常的处理
class MySafeSubscriber<T>(val actual: Subscriber<in T>) : Subscriber<T>(actual, true) {
    var done: Boolean = false

    //onNext() 内部的异常都会触发 onError(e) ，若连 onError() 都异常，将会调用 unsubscribe()
    override fun onNext(t: T) {
        try {
            if (!done)
                actual.onNext(t)
        } catch (e: Throwable) {
            Exceptions.throwIfFatal(e)//若是由于没有正确使用 rxJava 而异常，直接用 throw 挂掉整个 Observable，程序直接崩溃，从而让用户去改RxJava
            onError(e)//若上一行通过了，说明是 onNext 内部代码导致的异常，交给onError处理去
        }
    }

    //onCompleted 的异常也会触发 unsubscribe()
    override fun onCompleted() {
        if (!done) {
            done = true
            try {
                actual.onCompleted()
            } catch (e: Throwable) {
                Exceptions.throwIfFatal(e)
                try {
                    unsubscribe()//等价于 actual.unsubscribe() 取消订阅
                } catch (e: Throwable) {
                    RxJavaHooks.onError(e)//执行默认的异常处理方式,其实就是没处理~
                }
            }
        }
    }

    //程序是否崩溃是异常类型决定的，在 Observable.subscribe() 中判断了：若异常在 Exceptions.throwIfFatal(e) 中抛出，则崩溃
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
    MyObservable.create(
            Observable.OnSubscribe<Int> { t ->
                t?.onNext(3)
            })
            .subscribe(Action1 { t ->
                println(t)
            })
}