package li_ke.rxjavastudy.rxJava.test


import org.junit.Test
import rx.Observable
import java.util.concurrent.TimeUnit


/**
 * 作者: Li_ke
 * 日期: 2019/1/3 17:38
 * 作用:
 */

class Test {
	@Test
	fun start() {
//		Observable.timer(1, TimeUnit.SECONDS)
		Observable.error<String>(NullPointerException())
//		Observable
//			.merge(
//				Observable.just<String>("1"),
//				Observable.timer(100, TimeUnit.MILLISECONDS)
//					.map { throw NullPointerException() }
//			)
			.map { println("map1");"m1" }
//			.onErrorResumeNext { t ->
			//使用 onErrorResumeNext 处理错误,就不会走 onError 了
//				println("onErrorGo")
//				Observable.just("s")
//				Observable.empty()//空Observable不走onNext
//			}
			.doOnError { println("doOnError: $it") }
			.retryWhen {
				println("retryWhen")
				it.flatMap {
					println("in retryWhen")
//					if (it is NullPointerException) {
						Observable.timer(100, TimeUnit.MILLISECONDS)
//					} else
//						Observable.error<String>(CancellationException())
				}
			}
			.map { println("map2");"m2" }
			.delay(1, TimeUnit.SECONDS)
			.subscribe(
				{ println("next") },
				{ println("error") },
				{ println("com") }
			)

		Thread.sleep(1200)
	}
}