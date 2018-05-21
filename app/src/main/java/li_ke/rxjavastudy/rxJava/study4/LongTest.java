package li_ke.rxjavastudy.rxJava.study4;

import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

//结论： 找不出 Atomic 的使用环境
public class LongTest {

    private Long1 num = new Long1();

    private AtomicLong count = new AtomicLong(0);

    private TreeSet<Long> set = new TreeSet<>();

    public static void main(String[] args) {
        new LongTest().start();
    }

    private void start() {
//        new Thread(new LongTask(num), "t1").start();
//        new Thread(new LongTask(num), "t2").start();
//        new Thread(new LongTask(num), "t3").start();
//        new Thread(new LongTask(num), "t4").start();

        new Thread(new AtomicLongTask(count), "t1").start();
        new Thread(new AtomicLongTask(count), "t2").start();
        new Thread(new AtomicLongTask(count), "t3").start();
        new Thread(new AtomicLongTask(count), "t4").start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(set.size());
    }

    class AtomicLongTask implements Runnable {

        AtomicLong count;

        public AtomicLongTask(AtomicLong count) {
            this.count = count;
        }

        @Override
        public void run() {
            for (int i = 0; i < 100; i++) {
                count.getAndIncrement();
                System.out.println(Thread.currentThread().getName() + " ---> " + count);
                synchronized (AtomicLongTask.class) {
                    set.add(count.get());
                }
            }
        }

    }

    class LongTask implements Runnable {

        Long1 num;

        public LongTask(Long1 num) {
            this.num = num;
        }

        @Override
        public void run() {
            for (int i = 0; i < 100; i++) {
                num.i++;
                System.out.println(Thread.currentThread().getName() + " ---> " + num);
            }
        }

    }

    class Long1 {
        public long i = 0L;

        @Override
        public String toString() {
            return "" + i;
        }
    }
}
