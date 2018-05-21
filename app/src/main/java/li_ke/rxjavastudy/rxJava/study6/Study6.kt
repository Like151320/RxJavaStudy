

import rx.Observable
import rx.Subscriber

/**
 * 作者: Li_ke
 * 日期: 2018/5/17 17:34
 * lift()
 */
object Study6 {
    fun start() {
        Observable.create<String> {
            println("onSubscribe")
            it.onNext("1")
        }.lift<Int> { subscriber ->
            println("lift")
            //感觉像是自己手动实现 map
            return@lift object : Subscriber<String>() {
                override fun onNext(t: String?) {
                    //不同于 map() 的是，转换结果是手动 onNext() 而不是直接返回，有更高的灵活性
                    println("lift-onNext")
                    subscriber.onNext(t!!.toInt())
                }

                override fun onCompleted() {
                    subscriber.onCompleted()
                }

                override fun onError(e: Throwable?) {
                    subscriber.onError(e)
                }
            }
        }.subscribe {
            println(it)
        }
        /* 中心思想：
        lift() 就是 Subscriber 封装的抽象层，在上面代码中是我们手动实现了 Subscriber 的封装。
        对于 RxJava 的链式编程内部方法调用流程可以大致理解为
        1、【走 RxJava 表层方法】 一行一行走我们的代码，从上到下 create -> lift ：RxJava 内部做创建对象(封装)操作，执行完毕时 OnSubscribe 的引用树是 “下层嵌套上层”
        2、【走 RxJava 内部call】 我们的代码走到 subscribe()，subscribe() 中调用到 onSubscribe 的 call() ：RxJava 内部开始从逐个调用 OnSubscribe 的 call()。 看起来就是 “从下往上的执行 call() ”
                由于 call 中会创建 Subscriber，执行完毕时 Subscriber 的引用树是 “上层嵌套下层”
        3、【走我们的 onNext 】 call 的代码走到 create，create() 中调用到我们的 create代码块：开始由 我们调用onNext() 来逐个执行 Subscriber 的 onNext()。看起来就是 “从上往下执行 onNext()”
        有图 RxJava - 链式编程大致流程
         */
    }

    /*
    嘛~ 经过对 map() 的分析，感觉已经轻车熟路了。
    先看 lift() 源码

    《1》 // 熟悉的 create ，那么重点就是 OnSubscribeLift 和 Operator了
    public final <R> Observable<R> lift(final Operator<? extends R, ? super T> operator) {
        return create(new OnSubscribeLift<T, R>(onSubscribe, operator));
    }

    《2》 OnSubscribeLift 源码出奇的少
    @Override
    public void call(Subscriber<? super R> o) { // o == SafeSubscriber
        try {
            Subscriber<? super T> st = RxJavaHooks.onObservableLift(operator).call(o); // 即 operator.call(subscriber) ，结合我们的RxJava调用流程
            try { // 就是走我们的 lift代码块 ， st 就是我们在 lift代码块 中创建的那个 Subscriber
                // new Subscriber created and being subscribed with so 'onStart' it
                st.onStart();
                parent.call(st); // onSubscribe.call() = 走 我们创建的 onSubscribe。
            } catch (Throwable e) {
                // localized capture of errors rather than it skipping all operators
                // and ending up in the try/catch of the subscribe method which then
                // prevents onErrorResumeNext and other similar approaches to error handling
                Exceptions.throwIfFatal(e);
                st.onError(e);
            }
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
            // if the lift function failed all we can do is pass the error to the final Subscriber
            // as we don't have the operator available to us
            o.onError(e);
        }
    }

    看完 《1》《2》 后就知道了 OnSubscribeLift 只是直接调用我们的 lift代码块 来得到我们封装后的 Subscriber 而已，并没有像 map 那样主动封装 Subscriber ，复杂程度很低
    它也并不像 map 那样有双层封装来让顺序正序执行，所以 lift代码块 会被先执行。 但我们封装的 Subscriber 会符合顺序，正序执行

    由这种简单的结构可以发现，这种简单的结构很适合作为抽象层，让子层去扩展。随意定义 Operator 的实现类就能增加对 Subscriber 的封装操作
     */
}