import rx.Observable

/**
 * 作者: Li_ke
 * 日期: 2018/5/18 15:31
 * 作用:
 */
object Study7 {
    fun start() {
        Observable.create<Int> {
            println("create") // 第1个显示
            it.onNext(1)
        }.flatMap<String> { value ->
            //有点类似 lift，lift 中是手动封装 Subscriber，这里直接手动创建 Observable 了
            println("flatMap1") // 第2个显示
            return@flatMap Observable.create<String> {
                println("MyObservable1") // 第3个显示
                it.onNext("$value")
            }
        }.flatMap<Int> { value ->
            println("flatMap2") // 第4个显示
            return@flatMap Observable.create {
                println("MyObservable2") // 第5个显示
                it.onNext(value.toInt())
            }
        }.subscribe {
            println("subscribe:" + it) // 第6个显示
        }
    }

    /*

    《1》
    public final <R> Observable<R> flatMap(Func1<? super T, ? extends Observable<? extends R>> func) {
         if (getClass() == ScalarSynchronousObservable.class) {
             return ((ScalarSynchronousObservable<T>)this).scalarFlatMap(func);
         }
         return merge(map(func)); // 调用了 map 操作符，会封装一层 OnSubscribeMap，其 call 中创建 MapSubscriber 用于正序执行我们的 flatMap代码块。
    }

    《2》
    public static <T> Observable<T> merge(Observable<? extends Observable<? extends T>> source) {
        if (source.getClass() == ScalarSynchronousObservable.class) {
            return ((ScalarSynchronousObservable<T>)source).scalarFlatMap((Func1)UtilityFunctions.identity());
        }
        // map 操作后的 Observable 被 lift 操作，lift 只是一个转换类型的作用，重点在 OperatorMerge.<T>instance(false)
        return source.lift(OperatorMerge.<T>instance(false));
    }

    《3》
    public static <T> OperatorMerge<T> instance(boolean delayErrors) {
        if (delayErrors) {
            return (OperatorMerge<T>)HolderDelayErrors.INSTANCE;
        }
        return (OperatorMerge<T>)HolderNoDelay.INSTANCE; // 跟踪查找其类型，发现是一个常量
    }

    《4》
    static final class HolderNoDelay {
        /** A singleton instance. */ // 真正的常量对象
        static final OperatorMerge<Object> INSTANCE = new OperatorMerge<Object>(false, Integer.MAX_VALUE);
    }

    《5》
    @Override // 其call 是这样子的
    public Subscriber<Observable<? extends T>> call(final Subscriber<? super T> child) {
        MergeSubscriber<T> subscriber = new MergeSubscriber<T>(child, delayErrors, maxConcurrent); // 老样子，封装 child，有事先让老子 onNext，重点就是其 onNext 内部了
        MergeProducer<T> producer = new MergeProducer<T>(subscriber);
        subscriber.producer = producer; // 为 MergeSubscriber.producer 赋值，估计在 onNext 中有使用

        child.add(subscriber); // 添加到订阅队列
        child.setProducer(producer); //

        return subscriber;
    }

     */
}