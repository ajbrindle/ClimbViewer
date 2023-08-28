package com.sk7software.util.aspectlogger;

import android.util.Log;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class MethodLogger {
    private static final String POINTCUT_METHOD =
            "execution(@com.sk7software.util.aspectlogger.DebugTrace * *(..))";

    @Pointcut(POINTCUT_METHOD)
    public void methodAnnotatedWithDebugTrace() {}

    @Around("methodAnnotatedWithDebugTrace()")
    public Object weaveJoinPoint(ProceedingJoinPoint point) throws Throwable {
        long start = System.currentTimeMillis();
        String classMethod = point.getSignature().getDeclaringType().getSimpleName() + "." +
                MethodSignature.class.cast(point.getSignature()).getMethod().getName();
        StringBuilder args = new StringBuilder("Args: ");
        for (Object arg : point.getArgs()) {
            if (arg != null) {
                args.append(arg.toString() + ",");
            }
        }
        Log.d(point.getSignature().getDeclaringType().getSimpleName(),
                classMethod + " IN - " + args.toString());
        Object result = point.proceed();
        Log.d(point.getSignature().getDeclaringType().getSimpleName(),
                classMethod + " OUT - " +
                        "Result: " + result + "; Exec time: " +
                        (System.currentTimeMillis() - start) + "ms");
        return result;
    }
}
