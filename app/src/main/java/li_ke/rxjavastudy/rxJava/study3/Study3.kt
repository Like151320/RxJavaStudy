

import rx.Observable


/**
 * 作者: Li_ke
 * 日期: 2018/4/28 14:57
 * 异常处理
 */
object Study3 {
    fun start() {
        //先解析最简单的。
        Observable.create<Int> { it.onNext(1) }.subscribe { }
        /*
        中心思想： 不管怎样的多态、变化， RxJava 的使用形式永远是 Observable.subscribe() ，即 subscribe() 是不会变化的。
        而 subscribe() 中的内容也是固定不变的： onSubscribe.call( safeSubscriber ) ， 永远是 onSubscribe 的 call() 方法主导对 subscriber 的调用
        onSubscribe.call() 即是关键
        */
    }

    /*源码如下

    《1》
    public static <T> Observable<T> create(OnSubscribe<T> f) {//<T> 是内容的泛型
        return new Observable<T>(RxJavaHooks.onCreate(f));//传入 create() 的参数是订阅时执行的接口 OnSubscribe 如《2》
    }

    《2》
    public interface OnSubscribe<T> extends Action1<Subscriber<? super T>> {
        void call(T t);
    }

    接下来是 RxJavaHooks.onCreate(f)        Hooks（挂钩/钩子）
    这个方法传入 onSubscribe 返回 onSubscribe，而返回的 onSubscribe 用于创建了Observable
    如此看来其中必定对 onSubscribe 做了什么操作，点进去看下

    《3》
    public static <T> Observable.OnSubscribe<T> onCreate(Observable.OnSubscribe<T> onSubscribe) {
        Func1<Observable.OnSubscribe, Observable.OnSubscribe> f = onObservableCreate;//第一行代码，将 onObservableCreate 赋值给了Func1接口类型的 f
        //onObservableCreate 赋值的地方有多处，所以我直接在这里打了断点查看 onObservableCreate 的内容。
        if (f != null) {
            return f.call(onSubscribe);//发现 onObservableCreate 其实是在 initCreate() 中创建的实例。如《4》
        }
        return onSubscribe;
    }

    《4》
    onObservableCreate = new Func1<Observable.OnSubscribe, Observable.OnSubscribe>() {
            @Override
            public Observable.OnSubscribe call(Observable.OnSubscribe f) {
                return RxJavaPlugins.getInstance().getObservableExecutionHook().onCreate(f);//最终的onCreate(f)里，却是原封不动的返回了传入的参数
            }
        };

    所以可以吧代码简化一下：

    《1》
    public static <T> Observable<T> create(OnSubscribe<T> f) {
        return new Observable<T>(f);
    }

    Observable的构造中只是将传入参数保存了起来而已
    this.onSubscribe = f

    接下来看 Observable.subscribe() 方法内部

    《5》
    public final Subscription subscribe(final Action1<? super T> onNext) {
        if (onNext == null) {
            throw new IllegalArgumentException("onNext can not be null");//首先，传入值绝对不能为null
        }

        Action1<Throwable> onError = InternalObservableUtils.ERROR_NOT_IMPLEMENTED;//ERROR_NOT_IMPLEMENTED是一个实现Action1接口的常量，在Action1.call<Throwable>()中会直接抛出异常
        Action0 onCompleted = Actions.empty(); //Actions.empty()实现了接口 Action0 ~ ActionN ，但其call()中并没有做任何操作，即一个空实现的Action接口适配器。
        return subscribe(new ActionSubscriber<T>(onNext, onError, onCompleted));//那么重点就是这里了。
    }

    《6》
    //ActionSubscriber的工作也很简单，只是将传入构造的参数保存起来，并提供方法进行调用。
    public ActionSubscriber(Action1<? super T> onNext, Action1<Throwable> onError, Action0 onCompleted) {
        this.onNext = onNext;
        this.onError = onError;
        this.onCompleted = onCompleted;
    }

    《7》
    public final Subscription subscribe(Subscriber<? super T> subscriber) {
        return Observable.subscribe(subscriber, this);
    }

    《8》//终于到重点了，此时 subscribe是封装了我们传入的 (行为)<Action1>onNext 的 (行为订阅者)ActionSubscriber。而 observable是保存我们传入的 (订阅回调)onSubscribe 的Observable。
    static <T> Subscription subscribe(Subscriber<? super T> subscriber, Observable<T> observable) {

        if (subscriber == null) {//ActionSubscriber绝对不是null
            throw new IllegalArgumentException("subscriber can not be null");
        }
        if (observable.onSubscribe == null) {//如果我们没有传入onSubscriber，那么这里会报错
            throw new IllegalStateException("onSubscribe function can not be null.");
            /*
             * the subscribe function can also be overridden but generally that's not the appropriate approach
             * so I won't mention that in the exception
             */
        }

        // new Subscriber so onStart it
        subscriber.onStart();//ActionSubscriber.onStart()中并没有什么onStart，父类中也只是空方法，所以并没有任何的操作。

        /*
         * See https://github.com/ReactiveX/RxJava/issues/216 for discussion on "Guideline 6.4: Protect calls
         * to user code from within an Observer"
         */
        // if not already wrapped
        if (!(subscriber instanceof SafeSubscriber)) {//判定成功
            // assign to `observer` so we return the protected version
            subscriber = new SafeSubscriber<T>(subscriber);//已经包裹了一层的<Action1>onNext又被SafeSubscriber包裹了一层：this.actual = actual;
        }

        // The code below is exactly the same an unsafeSubscribe but not used because it would
        // add a significant depth to already huge call stacks.
        try {
            // allow the hook to intercept and/or decorate
            RxJavaHooks.onObservableStart(observable, observable.onSubscribe).call(subscriber);//见下面《9》，最终onObservableStart返回的依然是传入的 observable.onSubscribe，即我们的 (订阅回调)onSubscribe
            //操作我们传入的onSubscribe调用call()，将三层封装了 我们传入的action 的 SafeSubscriber 传入了进去
            //所以，我们调用的 subscribe() 中，得到的参数，就是这个SafeSubscriber。我们调用了 SafeSubscriber的onNext() -> 内部判断若事件未失效，走了 actual 即 ActionSubscriber 的onNext -> 内部直接调用了我们传入的 订阅回调onSubscribe
            //在SafeSubscriber中，当调用 onCompleted，或onError方法后，会标记已结束 isDone，就无法在走我们传入的 订阅回调call() 了。
            return RxJavaHooks.onObservableReturn(subscriber);//既然上面已经调用ok了，那么这里估计就是收尾工作了。见下面《10》
        } catch (Throwable e) {
            // special handling for certain Throwable/Error/Exception types
            Exceptions.throwIfFatal(e);//我们传入的代码块的执行，都被try了起来，一旦catch就开始进行异常捕获了。
            // in case the subscriber can't listen to exceptions anymore
            if (subscriber.isUnsubscribed()) {
                RxJavaHooks.onError(RxJavaHooks.onObservableError(e));
            } else {
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
            }
            return Subscriptions.unsubscribed();
        }
    }
}

    《9》
    public static <T> Observable.OnSubscribe<T> onObservableStart(Observable<T> instance, Observable.OnSubscribe<T> onSubscribe) {
        Func2<Observable, Observable.OnSubscribe, Observable.OnSubscribe> f = onObservableStart;//onObservableStart 实际就是在 init() 中创建实例的，内部只是返回了传入的第2个参数。
        if (f != null) {
            return f.call(instance, onSubscribe);
        }
        return onSubscribe;
    }

    《10》
    public static Subscription onObservableReturn(Subscription subscription) {
        Func1<Subscription, Subscription> f = onObservableReturn;//onObservableReturn 实际就是在 init() 中创建实例的，内部只是返回了传入的参数
        if (f != null) {
            return f.call(subscription);
        }
        return subscription;
    }

    综上所述 ~~~
    Observable.create<Int> { it.onNext(1) }.subscribe { } 中做了哪些事情呢?
    1、“Observable.create(OnSubscribe)” ： 创建一个Observable，将我们创建的 OnSubscribe(订阅回调) 保存。
    2、“.subscribe” ： 将我们创建的 Action1(行为) 进行两层包装后，调用 Observable.onSubscribe.call(action1)
    //就像这样 -> 看 Study3Code
    */
}

