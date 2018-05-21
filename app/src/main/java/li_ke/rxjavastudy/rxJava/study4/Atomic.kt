

import java.util.concurrent.atomic.AtomicLong

/**
 * 作者: Li_ke
 * 日期: 2018/5/16 10:27
 * 多线程操作一块内存时，实际操作是： 1、子线程从主线程中拷贝操作对象的内存。2、子线程对拷贝后的内存进行操作。3、子线程将修改后的内存拷贝回主线程。
 * 结论: Atomic 相当于带有同步锁的基本类型操作，但不知道使用环境是什么
 */
object Atomic {
    fun start() {
        val threads = arrayListOf<Thread>()
        for (i in 0..3) {
            /*threads.add(thread(start = false) {
                asyncFunSyncLock()
            })*/

            val thread: Thread = object : Thread() {
                override fun run() {
                    asyncFunAtomic()
//                    asyncFunNoAtomic()
                }
            }
            threads.add(thread)
        }

        for (thread in threads) {
            thread.start()
        }


        //wait,否则可能因主线程结束而直接停止所有线程
        Thread.sleep(1000)
    }

    //使用 synchronized 上锁
    private fun asyncFunSyncLock() {
        synchronized(Atomic::class.java) {
            for (i in 0..100) {
                print(i)
                print(" ")
            }
            println()
        }
    }

    //使用 Atomic 上锁
    var l: AtomicLong = AtomicLong(0L)

    private fun asyncFunAtomic() {// 上了同步锁的Long
        for (i in 0..100) {
            l.getAndIncrement()//自加1 原子操作
            print(" " + l.get())
        }
    }

    var l2 = 0L
    private fun asyncFunNoAtomic() {
        for (i in 0..100) {
            l2++
            print(" " + l2)
        }
    }
}