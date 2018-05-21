

import rx.Observable
import rx.Subscriber

/**
 * 作者: Li_ke
 * 日期: 2018/5/16 15:26
 * map()
 */
object Study5 {

    fun start() {
        //中间的计算符 map
        Observable.create<Int> { t: Subscriber<in Int>? ->
            t?.onNext(1)
        }.map { it: Int ->
            return@map it + 1
        }.map { it: Int ->
            return@map it + 1
        }.subscribe { it: Int ->
            println(it)
        }
        /* 中心思想：
        毫无疑问，RxJava 是设计模式大师，竟使用 对象 完成了递归操作。
        毫无疑问，关键点就是 运算符中封装的 OnSubscribe 类型、OnSubscribe 的call操作、call 中封装的 Subscriber 类型、Subscriber 的 onNext() 操作。
        封装 OnSubscribe 与 封装 Subscribe 这两层的封装很玄妙：
            每个运算符中 OnSubscribe 的封装将整个流程保存，但顺序是反的。
            每个 OnSubscribe.call 中 Subscriber 的封装将顺序纠正。
            最终我们手动调用 onNext() ，完美按顺序执行。
        */
    }

    /*
    《1》
    public final <R> Observable<R> map(Func1<? super T, ? extends R> func) { // func = 我们的 map 代码块
        return create(new OnSubscribeMap<T, R>(this, func)); // 使用 OnSubscribeMap 封装了我们的 map 代码块 , 竟然还持有了 this 即上一个 Observable 对象
        // 并返回了新创建的 Observable
    }

    《2》 OnSubscribeMap.call()
    @Override
    public void call(final Subscriber<? super R> o) { // 熟悉的 call()
        // o == SafeSubscriber 这是当然
        // transformer == 我们的 map 代码块
        // 当前的类型是 OnSubscribeMap ， 但调用这个 call() 的 Observable 是第二个 map 所创建的，所以其方法 Observable.subscribe() 中的代码 onSubscribe.call() ，
        // 这个 onSubscribe，也就是当前对象，是我们调用第二个 map() 时创建的
        MapSubscriber<T, R> parent = new MapSubscriber<T, R>(o, transformer); // MapSubscriber 封装了 SafeSubscriber 与第二个 map 代码块
        o.add(parent);
        source.unsafeSubscribe(parent); // source == 第一个 map() 时创建的 Observable
    }

    《3》 subscriber.add()
    public final void add(Subscription s) {
        subscriptions.add(s); // 这里用到了 subscriptions，但我们从未了解过这个对象
    }

    《4》 Subscriber 的构造中对 subscriptions 进行了初始化。
    protected Subscriber(Subscriber<?> subscriber, boolean shareSubscriptions) {
        this.subscriber = subscriber;
        this.subscriptions = shareSubscriptions && subscriber != null ? subscriber.subscriptions : new SubscriptionList();
    }

    subscriptions 的操作 由实际流程得知，第一层的 ActionSubscriber 调用了 -> Subscriber() -> Subscriber(null,false)，并没有初始化 subscriptions。还没有订阅线
    第二层的 SafeSubscriber 调用了 -> Subscriber(actionSubscriber,true)，赋值了： subscriptions = new SubscriptionList(); 有了无内容的订阅线
    第三层的 MapSubscriber 调用了 -> Subscriber() -> Subscriber(null,false)，并没有对订阅线进行再次初始化。
    第三层的 MapSubscriber 是直接用 subscriber.add(subscription) 操作了订阅线，为其加了一条预约。加到了 safeSubscriber.subscriptions 中

    《5》 第一个 Observable 的 unsafeSubscribe()
    public final Subscription unsafeSubscribe(Subscriber<? super T> subscriber) { // subscribe == 在《2》中创建的 MapSubscriber
        try {
            // new Subscriber so onStart it
            subscriber.onStart(); // 实际是 Subscriber.onStart 无操作
            // allow the hook to intercept and/or decorate
            // 当前的 Observable 是第一个 map 时创建的，调用 onSubscribe.call(subscriber)
            // onSubscribe 是在当前的 Observable 构造时初始化的，那么它的 onSubscribe 自然是我们第一个 map代码块 的封装类 OnSubscribeMap ，它是在第一个 map() 时创建的。
            RxJavaHooks.onObservableStart(this, onSubscribe).call(subscriber);
            return RxJavaHooks.onObservableReturn(subscriber);
        } catch (Throwable e) {
            // special handling for certain Throwable/Error/Exception types
            Exceptions.throwIfFatal(e);
            // if an unhandled error occurs executing the onSubscribe we will propagate it
            try {
                subscriber.onError(RxJavaHooks.onObservableError(e));
            } catch (Throwable e2) {
                Exceptions.throwIfFatal(e2);
                // if this happens it means the onError itself failed (perhaps an invalid function implementation)
                // so we are unable to propagate the error correctly and will just throw
                RuntimeException r = new OnErrorFailedException("Error occurred attempting to subscribe [" + e.getMessage() + "] and then again while trying to pass to onError.", e2);
                // TODO could the hook be the cause of the error in the on error handling.
                RxJavaHooks.onObservableError(r);
                // TODO why aren't we throwing the hook's return value.
                throw r; // NOPMD
            }
            return Subscriptions.unsubscribed();
        }
    }

    《6》 第二次调用 call()
    @Override
    public void call(final Subscriber<? super R> o) { // o == 在第一次 call()《2》 中创建的 MapSubscriber，保存着 SafeSubscriber 和我们的第二个 map代码块
        // 当前类是我们第一个 map() 时创建的封装类 OnSubscribeMap，保存最开始的 observable 和我们的第一个 map代码块
        MapSubscriber<T, R> parent = new MapSubscriber<T, R>(o, transformer); // 又 new 了个 MapSubscriber，将 MapSubscriber 再次封装，并传入了 我们的第一个 map代码块
        o.add(parent);
        source.unsafeSubscribe(parent); // source 是最开始的 observable。
    }

    《7》 最开始的 Observable 调用
    public final Subscription unsafeSubscribe(Subscriber<? super T> subscriber) {
        try {
            // new Subscriber so onStart it
            subscriber.onStart();
            // allow the hook to intercept and/or decorate
            // onSubscribe 是 Observable 构造时传入的，当前的 Observable 是最开始的，那么 onSubscribe 当然是我们最开始的 subscribe代码块
            // 终于， call()，传入了 subscriber ，即二次封装的 MapSubscriber。
            RxJavaHooks.onObservableStart(this, onSubscribe).call(subscriber); // 当然，之后就会执行到我们的 subscribe代码块了
            return RxJavaHooks.onObservableReturn(subscriber);
        } catch (Throwable e) {
            // special handling for certain Throwable/Error/Exception types
            Exceptions.throwIfFatal(e);
            // if an unhandled error occurs executing the onSubscribe we will propagate it
            try {
                subscriber.onError(RxJavaHooks.onObservableError(e));
            } catch (Throwable e2) {
                Exceptions.throwIfFatal(e2);
                // if this happens it means the onError itself failed (perhaps an invalid function implementation)
                // so we are unable to propagate the error correctly and will just throw
                RuntimeException r = new OnErrorFailedException("Error occurred attempting to subscribe [" + e.getMessage() + "] and then again while trying to pass to onError.", e2);
                // TODO could the hook be the cause of the error in the on error handling.
                RxJavaHooks.onObservableError(r);
                // TODO why aren't we throwing the hook's return value.
                throw r; // NOPMD
            }
            return Subscriptions.unsubscribed();
        }
    }

    直到《7》，终于执行到了我们的 subscribe代码块，差点没累死，接着分析：
    传入 subscriber代码块的 subscriber 正是在 OnSubscribeMap 中封装了两层的 MapSubscriber。现在它的结构是这样的：
            当前的MapSubscriber - 在第二个 Observable.subscribe() 时创建的
                持有 -> 第一个 map代码块
                持有 -> 下层的MapSubscriber - 在第一个Observable.unsafeSubscribe() 时创建的
                            持有 -> 第二个 map代码块
                            持有 -> 下一层 SafeSubscriber
                                        持有 -> ActionSubscriber
                                                持有 -> subscribe代码块
    最后，MapSubscriber.onNext()

    《8》
    @Override
    public void onNext(T t) {
        R result; // 保存 我们的 map代码块 算出的结果

        try {
            result = mapper.call(t); // 哈，我们的 map代码块 开始运行了
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex); // Study3 中的代码异常拦截机制
            unsubscribe();
            onError(OnErrorThrowable.addValueAsLastCause(ex, t));
            return;
        }
        actual.onNext(result); // 哟，走下一个 Subscriber 的 onNext 了。 分析完上面一大堆，这里复杂程度都不用分析了
    }

    把上述流程整理一下，总的流程如下
    若要更直观的梳理流程，去看 XMind 思维导图
    尝试还原：Study5Code

    Observable.create()
        —— 创建(开始的) Observable 保存我们的 onSubscribe代码块
           observable.onSubscribe 就是我们的 onSubscribe代码块
    .map()
        —— 创建(第一个) Observable 与(第一个) OnSubscribeMap
           onSubscribeMap.transformer 就是我们的(第一个) map代码块
           onSubscribeMap.source 就是(开始的) observable
           observable.onSubscribe 就是(第一个) OnSubscribeMap
    .map()
        —— 创建(第二个) Observable 与(第二个) OnSubscribeMap
           onSubscribeMap.transformer 就是我们的(第二个) map代码块
           onSubscribeMap.source 就是(第一个) observable
           observable.onSubscribe 就是(第二个) OnSubscribeMap
    .subscribe()
        —— 封装 Subscriber -> ActionSubscriber -> SafeSubscriber 见 Study3
        —— 执行(第二个) OnSubscribeMap.call(SafeSubscribe)
                {
                    封装(第二个) map代码块: transformer 为 (第一个) MapSubscriber
                         —— mapSubscriber.actual 就是 safeSubscriber
                            mapSubscriber.mapper 就是(第二个) map代码块
                    调用(第一个) Observable -> source.unsafeSubscribe(mapSubscriber)
                }

            —— 执行(第一个) Observable.unsafeSubscribe( (第一个) mapSubscriber )
                {
                    调用(第一个) OnSubscribeMap: onSubscribe.call(mapSubscriber)
                }

                —— 执行(第一个) OnSubscribeMap.call( (第一个) mapSubscriber)
                    {
                        封装(第一个) map代码块: transformer 为 (第二个) MapSubscriber
                             —— mapSubscriber.actual 就是(第一个) mapSubscriber
                                mapSubscriber.mapper 就是(第一个) map代码块
                        调用(开始的) Observable -> source.unsafeSubscribe(mapSubscriber)
                    }

                    —— 执行(开始的) Observable.unsafeSubscribe( (第二个) MapSubscriber )
                        {
                            调用 OnSubscriber代码块: onSubscribe.call() - 【我们的 onSubscribe代码块执行】
                        }

                    我们在 onSubscribe 中调用 onNext()

                        —— 执行(第二个) MapSubscriber.onNext(t)
                            {
                                计算(第一个) map代码块: mapper.call(t) - 【我们的(第一个) map代码块执行】
                                调用(第一个) MapSubscriber: actual.onNext(r)
                            }

                            —— 执行(第一个) MapSubscriber.onNext(t)
                                {
                                    计算(第二个) map代码块: mapper.call(t) - 【我们的(第二个) map代码块执行】
                                    调用 Subscriber: actual.onNext(r)
                                }

                                —— 执行 SafeSubscriber.onNext(t)
                                    {
                                        调用 ActionSubscribe: actual.onNext(t)
                                    }

                                    —— 执行 ActionSubscriber.onNext(t)
                                        {
                                            调用 Subscriber.onNext(t) - 【我们的 subscriber代码块执行】
                                        }
     */
}