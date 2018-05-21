package li_ke.rxjavastudy.rxJava.study2

import li_ke.rxjavastudy.rxJava.study2.MySubscriber
import rx.Observable

/**
 * 作者: Li_ke
 * 日期: 2018/4/28 13:34
 * 作用:
 */
//把OnSubscribe抽象为接口，增加 直观性
internal interface MyOnSubscribe<out T> {
    fun call(subscriber: MySubscriber<T>)
}

internal object MyOnSubscribeFactory {

    internal fun <T> create(onSubscribe: (MySubscriber<T>) -> Unit): MyOnSubscribe<T> {
        return createMyOnSubscribe { subscriber ->
            onSubscribe(subscriber)
        }
    }


    internal fun <T> create(array: Array<out T>): MyOnSubscribe<T> {
        return createMyOnSubscribe { subscriber ->
            for (t in array) {
                subscriber(t)
            }
        }
    }

    fun <T1, T2, T> create(list: List<Observable<out Any?>?>, onSubscribeAll: (t1: T1, t2: T2) -> T): MyOnSubscribe<T> {
        TODO("大作死，水平有限，毫无头绪，我还是看源码吧")
    }
}

/**lambda -> 接口 的转换。使用：通过调用 call 启动lambda*/
private fun <T> createMyOnSubscribe(block: (MySubscriber<T>) -> Unit): MyOnSubscribe<T> {
    return object : MyOnSubscribe<T> {
        override fun call(subscriber: MySubscriber<T>) {
            block(subscriber)
        }
    }
}