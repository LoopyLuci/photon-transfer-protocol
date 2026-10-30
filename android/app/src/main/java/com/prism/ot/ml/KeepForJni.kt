package com.prism.ot.ml

/**
 * Marker annotation. Any class or member annotated with this is referenced
 * by name from the Rust JNI bridge and must therefore survive R8/ProGuard
 * minification. The ProGuard rules in `app/proguard-rules.pro` enforce this.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR
)
annotation class KeepForJni
