

import rx.Observable

/**
 * 作者: Li_ke
 * 日期: 2018/5/15 11:08
 * 灵活的 onSubscribe.call()
 */
object Study4 {
    fun start() {
        //解析 from - 多个 OnSubscribe(订阅回调)
        Observable.from(arrayOf(1, 2, 3, 4, 5)).subscribe { print(it) }
        /* 中心思想： 使用多态扩展了 OnSubscribe.call() 使其对 subscribe 的调用形式发生变化。
           最终实现多次调用 subscribe。

           直到这里，概括一下 RxJava 的所作所为：

           1、抽象+装饰 subscribe 与 .subscribe() 方法配合，将异常进行捕获，统一交给 onError() 处理(非rxJava使用不当的异常)。
           2、抽象 onSubscribe 来灵活的实现对 subscribe 的调用。

         */
    }
    /*
    看源码得知，对于并不是 OnSubscribe 的数组，rxJava 使用 OnSubscribeFromArray 将其封装成了 OnSubscribe

    《1》
    public static <T> Observable<T> from(T[] array) {
        int n = array.length;
        if (n == 0) {
            return empty();
        } else
        if (n == 1) {
            return just(array[0]);
        }
        return create(new OnSubscribeFromArray<T>(array));
    }

    后面的 .subscribe() 还是老样子，两层封装 action 后，调用 OnSubscribe 的 call()
    那么关键部分，就得看它的 call() 了：

    《2》
    @Override
    public void call(Subscriber<? super T> child) { // child 就是我们的 subscriber 封装后的 SafeSubscriber
        // 即调用 SafeSubscriber.setProducer()
        child.setProducer(new FromArrayProducer<T>(child, array)); // 先 new FromArrayProducer 再传给 setProducer
    }

    《3》 // 代码较多，一点一点分析
    public void setProducer(Producer p) { // 首先，这个参数 p 是刚刚的 new FromArrayProducer() ，里面有我们传入的 onSubscribe(订阅回调) 与 subscriber(订阅内容)
        long toRequest; // 标记：操作内容
        boolean passToSubscriber = false; // 标记：跳过这个 Subscriber
        synchronized (this) { // 线程锁只是为了确定 操作标记
            toRequest = requested;
            producer = p; // 将 onSubscribe与subscriber 保存在内部
            if (subscriber != null) { // 由于 this == SafeSubscriber ， 这里的 subscriber 就是在 SafeSubscriber 创建时传入的 ActionSubscriber ，是通过 Subscriber 中的构造赋值的
                // middle operator ... we pass through unless a request has been made
                if (toRequest == NOT_SET) {
                    // we pass through to the next producer as nothing has been requested
                    passToSubscriber = true;
                }
            }
        }
        // do after releasing lock
        if (passToSubscriber) {
            subscriber.setProducer(producer); // 第一次是 SafeSubscriber.setProducer() ， 第二次就是内部的 ActionSubscriber.setProducer() 。 而第二次的 ActionSubscriber 的 subscriber 并没有被赋值,所以停止了递归
        } else {
            // we execute the request with whatever has been requested (or Long.MAX_VALUE) // 按上述逻辑，只有 ActionSubscriber(subscriber==null) 或 requested != NOT_SET 的 Subscriber 才会执行这里，才会走实际操作
            if (toRequest == NOT_SET) {
                producer.request(Long.MAX_VALUE); // 第二次的 ActionSubscriber.setProducer() 会执行到这里
            } else {
                producer.request(toRequest);
            }
        }
    }

    《4》
    @Override
    public void request(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("n >= 0 required but it was " + n);
        }
        if (n == Long.MAX_VALUE) {
            if (BackpressureUtils.getAndAddRequest(this, n) == 0) {
                fastPath(); // 走到这里
            }
        } else
        if (n != 0) {
            if (BackpressureUtils.getAndAddRequest(this, n) == 0) {
                slowPath(n);
            }
        }
    }


    《5》
    void fastPath() {
        final Subscriber<? super T> child = this.child; // child == SafeSubscriber

        for (T t : array) {
            if (child.isUnsubscribed()) { // 检测有效性
                return;
            }

            child.onNext(t); // 终于执行了
        }

        if (child.isUnsubscribed()) {
            return;
        }
        child.onCompleted(); // 最后走一遍 onCompleted()
    }


    嘛，晕晕乎乎的总算走到 subscriber 被执行了。但那些代码块究竟有什么作用呢？ 重新理一下

    《1》中创建了 from() 创建了 OnSubscribeFromArray(array) ，搞事之旅也从这里开始了。
    《2》中发现： OnSubscribeFromArray.call() 中使用了 Subscriber.setProducer(FromArrayProducer) 。 啊，原来 Subscriber 和 FromArrayProducer 也在一起搞事
    《3》中发现了 Subscriber.setProducer 是如何搞事的：
            1、Subscriber知道自己是装饰模式，它使用递归来解开封装，直到 subscriber == null 的 ActionSubscriber
            2、让 ActionSubscriber.request 去执行搞事
    《4》中是 ActionSubscriber.request 的搞事经过：
            1、 BackpressureUtils.getAndAddRequest(this, n) == 0 // 待分析
            2、 fastPath(); // 开始执行我们的代码
    《5》中是执行我们代码的经过：
            1、检测未解绑 (isUnsubscribed)
            2、执行 safeSubscriber.onNext() 走我们的代码
            3、safeSubscriber.onCompleted() 走我们的代码or默认结束方法

     经过分析，这一套操作中，只有 《1》+《2》+《5》 是浅显易懂的： 封装 array 为 OnSubscribe 、 在 call 中 执行我们的代码
        那么 《3》+《4》 中是在做什么呢？

     在《3》中，Subscriber 让 ActionSubscriber.request() 去执行，为什么非 ActionSubscriber 不可呢？
     《4》中的重点是《6》：

     《6》
     public static long getAndAddRequest(AtomicLong requested, long n) {
        // add n to field but check for overflow
        while (true) {
            long current = requested.get();
            long next = addCap(current, n);
            if (requested.compareAndSet(current, next)) { // 这里涉及到 Atomic 可以理解为 requested = next
                return current;
            }
        }
    }

    《7》
    public static long addCap(long a, long b) { // 返回值 > 0 ，或是 MAX_VALUE
        long u = a + b;
        if (u < 0L) {
            u = Long.MAX_VALUE;
        }
        return u;
    }

    但是，按照实际调用顺序来看，在 《3》 的 toRequest == NOT_SET 时，《4》会调用《5》来走我们的方法，并没有用 AtomicLong 进行计算，
    而在 《5》 源码的下面有 slowPath() ，这个方法才是与 AtomicLong 进行了计算。所以在这套流程中就暂不考虑《3》《4》《6》《7》，关键代码就是《5》


    综上所述，使用from即：Observable 先将传入的数组封装为 《1》OnSubscribeFromArray，扩展 《2》call() 使其遍历 《5》subscriber。
    复原：- Study4Code
    */

}