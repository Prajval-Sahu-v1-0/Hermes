package com.hermes.feature;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AOP Aspect that intercepts methods annotated with @FeatureGuard.
 * 
 * Guards method execution based on feature enablement:
 * - If feature is ENABLED: proceed with normal execution
 * - If feature is not ENABLED: return appropriate empty value
 * 
 * Logs skip reason once per feature to avoid spam.
 */
@Aspect
@Component
@Order(1) // Execute before other aspects
public class FeatureGuardAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureGuardAspect.class);

    private final FeatureRegistry featureRegistry;

    // Track which features have been logged as skipped (log once per feature)
    private final Set<FeatureFlag> loggedSkips = ConcurrentHashMap.newKeySet();

    public FeatureGuardAspect(FeatureRegistry featureRegistry) {
        this.featureRegistry = featureRegistry;
    }

    /**
     * Intercepts all methods annotated with @FeatureGuard.
     */
    @Around("@annotation(com.hermes.feature.FeatureGuard)")
    public Object guardFeature(ProceedingJoinPoint joinPoint) throws Throwable {
        // Get the annotation
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        FeatureGuard guard = method.getAnnotation(FeatureGuard.class);

        if (guard == null) {
            return joinPoint.proceed();
        }

        FeatureFlag flag = guard.value();
        FeatureState state = featureRegistry.getState(flag);

        // If enabled, proceed normally
        if (state.isActive()) {
            return joinPoint.proceed();
        }

        // Not enabled - log once and return empty
        logSkipOnce(flag, state, method.getName(), guard.logSkip());
        return getEmptyReturnValue(method.getReturnType());
    }

    /**
     * Logs skip message once per feature to avoid log spam.
     */
    private void logSkipOnce(FeatureFlag flag, FeatureState state, String methodName, boolean forceLog) {
        if (forceLog || loggedSkips.add(flag)) {
            log.debug("[FeatureGuard] Skipping {}.{}() - feature {} is {}",
                    flag.name(), methodName, flag.name(), state.name());
        }
    }

    /**
     * Returns an appropriate empty value based on return type.
     */
    private Object getEmptyReturnValue(Class<?> returnType) {
        // Optional
        if (Optional.class.isAssignableFrom(returnType)) {
            return Optional.empty();
        }

        // List
        if (List.class.isAssignableFrom(returnType)) {
            return Collections.emptyList();
        }

        // Set
        if (Set.class.isAssignableFrom(returnType)) {
            return Collections.emptySet();
        }

        // Boolean primitives
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == Boolean.class) {
            return Boolean.FALSE;
        }

        // Numeric primitives
        if (returnType == int.class || returnType == Integer.class) {
            return 0;
        }
        if (returnType == long.class || returnType == Long.class) {
            return 0L;
        }
        if (returnType == double.class || returnType == Double.class) {
            return 0.0;
        }

        // Void
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }

        // Default: null for objects
        return null;
    }
}
