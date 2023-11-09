package org.swqxdba.logicDelete.util;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class JLogicUtil {

    /**
     * 在一个代码块中禁用逻辑删除拦截 注意 jpa等框架可能在commit时候才执行sql 请保证代码块内的sql在方法返回之前执行完毕
     */
    public static void closeHandleScope(Runnable runnable) {
        try {
            JLogicThreadLocal.enableHandle.set(false);
            runnable.run();
        } finally {
            JLogicThreadLocal.enableHandle.set(true);
        }
    }

    /**
     * 在一个代码块中禁用逻辑删除拦截 注意 jpa等框架可能在commit时候才执行sql 请保证代码块内的sql在方法返回之前执行完毕
     */
    public static <T> T closeHandleScope(Supplier<T> supplier) {
        try {
            JLogicThreadLocal.enableHandle.set(false);
            return supplier.get();
        } finally {
            JLogicThreadLocal.enableHandle.set(true);
        }
    }


    /**
     * @return 当前线程是否需要进行逻辑删除处理
     */
    public static boolean whetherHandleEnable() {
        Boolean enable = JLogicThreadLocal.enableHandle.get();
        return enable == null || enable;
    }

    /**
     * 设置当前线程是否需要进行逻辑删除处理
     */
    public static void setHandleEnableState(boolean enableState) {
        JLogicThreadLocal.enableHandle.set(enableState);
    }
}
