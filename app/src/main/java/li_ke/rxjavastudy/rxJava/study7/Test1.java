package li_ke.rxjavastudy.rxJava.study7;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
面试上的一道题，再考虑优化和内存占用的情况下 用 && 分隔一串字符串。
得出结论 纯算法匹配由于Regex(因为内部太大)。使用String[] 优于 List(因为内部太大)
 */
public class Test1 {

    //	public static final String str = "a&&b&&c&&d&&e&&f&&g";
    public static final String str = "ab&&cde&&fghi&&jk&&lmn&&op&&q*";

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
//		for (int i = 0; i < 1000000; i++) {
////			noRegex(str);
//			regex(str);
//		}
        regex(str);
        long end = System.currentTimeMillis();
        System.out.println((end - start) + " ms");
    }

    public static List<String> regex(String str) {
//		List<String> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("&&");
        Matcher matcher = pattern.matcher("&&");
        int start, end, e = 0;
        while (matcher.find()) {
            start = matcher.start();
            end = matcher.end();
//        	list.add(str.substring(e, start));
            e = end;
        }
//        if(e > 0) {
//        	list.add(str.substring(e, str.length()));
//        }
        return null;
    }

    public static String[] noRegex(String str) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            //若发现两个 &&
            if (c == '&')
                if (i + 1 < str.length())
                    if (str.charAt(i + 1) == '&') {
                        i += 1;
                        count++;
                    }
        }
        if (count > 0) {
            int start = 0, oldStart = 0;
            String[] array = new String[count + 1];
            count = 0;
            for (int i = 0; i < str.length(); i++) {

                //若发现两个 &&
                char c = str.charAt(i);
                if (c == '&')
                    if (i + 1 < str.length())
                        if (str.charAt(i + 1) == '&') {
                            start = i;
                            i += 1;


                            String s = str.substring(oldStart == 0 ? 0 : oldStart + 2, start);
                            array[count++] = s;
                            oldStart = start;
                        }
            }
            if (oldStart > 0) {
                String s = str.substring(oldStart + 2, str.length());
                array[count++] = s;
            }
//    	  
//    	  for(int i =0 ;i<array.length;i++) {
//    		  System.out.println(array[i]);
//    	  }
        }

        return null;
    }
}
