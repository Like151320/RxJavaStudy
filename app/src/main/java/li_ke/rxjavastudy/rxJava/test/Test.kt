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
			.doOnError { println("doOnError") }
			//使用 onErrorResumeNext 处理错误,就不会走 onError 了

			//直接使用 retry 判断重试次数
			.retry { i, t ->
				println(i)
				i < 3
			}
//			.retryWhen { attempts ->
//				//出现错误的 observable
//				attempts.flatMap {
//					//使用 flatMap添加重试订阅逻辑，当出现错误时走flatMap内部
//					//执行重试语句
//					Observable.create<Int> { it.onNext(1) }
//				}
//			}
			.delay(1, TimeUnit.SECONDS)
			.subscribe(
				{ println("next") },
				{ println("error") },
				{ println("com") }
			)

		Thread.sleep(1200)
	}
}