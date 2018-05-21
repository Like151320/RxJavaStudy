package li_ke.rxjavastudy.rxJava.study1

import rx.Observable
import rx.Subscriber

/**
 * 作者: Li_ke
 * 日期: 2018/4/28 11:16
 * 作用:
 */
object Study1 {
    fun rxJavaTest() {
        //RxJava学习记录

        /* 首先，要知道英文单词的含义，
           observe（观察、说、注意到）
           observer（观察者）
           able（有能力的、能够）
           observable（可观察的事物、看得见的）
           subscribe（订阅）——常见于YouTube
           subscriber（订阅者）——点击subscribe，你就是youTuber的Subscriber了
           onSubscribe（当订阅时）——常见于 onCreate()、onStart() - 当创建时、当开始时
        */

        /*在Java的远古版本（JDK1.0）。有个基础的监听工具
        val observable = java.util.Observable()
        observable.addObserver { o, arg -> println("观察到改变了$arg") }
        observable.notifyObservers("-洒洒")
        内部非常简单的几个方法，甚至泛型都没有，只是监听而已。所以无视这个类，直接看rx包下的Observer
        */

        //首先最基本使用方式
        Observable.create<String> { sub: Subscriber<in String>? ->
            //1、使用create()创建一个被观察者（Observable），并传入一个 “当订阅时执行” 的代码块 OnSubscribe，在这里称为 “开始事件”
            println("YouTuber发布视频啦！")
            sub?.onNext("“发布ok”")//2、开始事件代码块中，包含一个订阅者（subscriber），如此一来，一旦订阅了，就可以立即通知订阅者。
        }.subscribe { str ->
            //3、被观察者使用订阅（subscribe）方法，添加订阅者，一旦事件完成，会通知订阅者。
            println("subscriber看到通知啦！ - $str")
        }

        //我尝试通过外部的调用方式，还原一下它的内部实现
        MyObservable.create<String> { sub: MySubscriber<in String>? ->
            println("YouTuber发布视频啦！")
            sub?.onNext("“发布ok”")
        }.subscribe { str ->
            println("subscriber看到通知啦！ - $str")
        }
    }


}


//第一次还原

private typealias MySubscriber<T> = (T) -> Unit//由于Kotlin的接口无法像java那样用lambda表达式创建，所以写成了Kotlin的类型别名，其实就是只有一个方法的接口
private typealias MyOnSubscribe<T> = (MySubscriber<T>) -> Unit

private fun <T> MySubscriber<T>.onNext(t: T) = this.invoke(t)


private class MyObservable<out T> private constructor(private val onSubscribe: MyOnSubscribe<T>? = null) {

    fun subscribe(subscriber: (T) -> Unit) {//订阅方法，一旦被订阅，整个系统就开始运作
        //启动 当订阅时执行的方法（onSubscribe）,传入订阅者（Subscriber）
        onSubscribe?.invoke(subscriber)
        //当onSubscribe执行结束后，整个系统就结束了
    }

    companion object {
        //被观察者不能直接new，是由 create() 创建的，并在创建时传入保存了一个 当订阅时（OnSubscribe）的代码块
        fun <T> create(onSubscribe: MyOnSubscribe<T>): MyObservable<T> {
            return MyObservable(onSubscribe)
        }
    }
}