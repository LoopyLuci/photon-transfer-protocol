// core/src/jni/codec.rs — outline of required exports.
// Every function receives a `JNIEnv` and the Java object reference first.

use jni::objects::{JByteArray, JClass, JObject, JString};
use jni::sys::{jboolean, jint, jlong, jbyteArray, jobjectArray};
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_com_prism_ot_codec_CodecBridgeImpl_nativeVersion(
    env: JNIEnv, _class: JClass,
) -> jstring { /* return env!("CARGO_PKG_VERSION") */ }

#[no_mangle]
pub extern "system" fn Java_com_prism_ot_codec_CodecBridgeImpl_createEncoder(
    env: JNIEnv, _class: JClass, config: JObject,
) -> jlong { /* deserialize SessionConfig, build Encoder, Box::into_raw as jlong */ }

#[no_mangle]
pub extern "system" fn Java_com_prism_ot_codec_CodecBridgeImpl_beginEncoding(
    env: JNIEnv, _class: JClass, handle: jlong,
    file: JByteArray, header: JByteArray,
) -> jint { /* split into segments, fountain-encode, store tile iterator */ }

#[no_mangle]
pub extern "system" fn Java_com_prism_ot_codec_CodecBridgeImpl_nextEncodedTile(
    env: JNIEnv, _class: JClass, handle: jlong,
) -> jobject { /* build EncodedTileBytes or return null */ }

// ... etc. for every method in CodecBridgeImpl.
