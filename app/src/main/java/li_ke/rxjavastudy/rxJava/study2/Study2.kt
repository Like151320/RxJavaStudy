package li_ke.rxjavastudy.rxJava.study2

import rx.Observable

/**
 * 作者: Li_ke
 * 日期: 2018/4/28 11:16
 * 作用:
 */
object Study2 {
    fun rxJavaTest() {

        //尝试还原下更多的用法
        Observable
                .just(0, 1, 2, 3, 4, 5)//开始事件（onSubscribe） 中有多个内容 遍历它们
                .lift<String> {
                    it.onNext("s")
                    return@lift null
                }.subscribe {
                    println("遍历中 $it")
                }

        Observable
                //有多个 “YouTuber”（Observable），当他们都“发布视频后”（OnSubscribe），再通知订阅者（subscriber）
                .zip(
                        Observable.create<String> { it.onNext("发布事件1") },
                        Observable.create<StringBuilder> { it.onNext(StringBuilder("发布事件2")) },
                        { t1, t2 ->
                            t1 + t2
                        }
                )
                .subscribe {
                    println(it)
                }


        //改变 开始事件的结构，以适应多个事件
        MyObservable
                .just(0, 1, 2, 3, 4, 5)
                .subscribe {
                    println("遍历中 $it")
                }

        MyObservable
                .zip(
                        Observable.create<String> { it.onNext("发布事件1") },
                        Observable.create<StringBuilder> { it.onNext(StringBuilder("发布事件2")) },
                        { t1, t2 ->
                            t1 + t2
                        }
                )
                .subscribe {
                    println(it)
                }
    }
}


//第二次还原
internal typealias MySubscriber<T> = (T) -> Unit

private class MyObservable<out T> private constructor(private val onSubscribe: MyOnSubscribe<T>? = null) {
    fun subscribe(subscriber: (T) -> Unit) {
        onSubscribe?.call(subscriber)
    }

    companion object {
        fun <T> create(onSubscribe: (MySubscriber<T>) -> Unit): MyObservable<T> {
            //由于创建 开始事件 （OnSubscribe）的方式变多，使用了工厂模式
            val sub: MyOnSubscribe<T> = MyOnSubscribeFactory.create(onSubscribe)
            return MyObservable(sub)
        }

        fun <T> just(vararg t: T): MyObservable<T> {
            val sub: MyOnSubscribe<T> = MyOnSubscribeFactory.create(t)
            return MyObservable(sub)
        }


        fun <T1, T2, T> zip(o1: Observable<T1>?, o2: Observable<T2>?, onSubscribeAll: (t1: T1, t2: T2) -> T): MyObservable<T> {
            val sub: MyOnSubscribe<T> = MyOnSubscribeFactory.create(listOf(o1, o2), onSubscribeAll)
            return MyObservable(sub)
        }
    }
}