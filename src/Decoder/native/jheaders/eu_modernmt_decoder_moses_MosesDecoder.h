/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class eu_modernmt_decoder_moses_MosesDecoder */

#ifndef _Included_eu_modernmt_decoder_moses_MosesDecoder
#define _Included_eu_modernmt_decoder_moses_MosesDecoder
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     eu_modernmt_decoder_moses_MosesDecoder
 * Method:    init
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_eu_modernmt_decoder_moses_MosesDecoder_init
  (JNIEnv *, jobject, jstring);

/*
 * Class:     eu_modernmt_decoder_moses_MosesDecoder
 * Method:    getFeatures
 * Signature: ()[Leu/modernmt/decoder/moses/MosesFeature;
 */
JNIEXPORT jobjectArray JNICALL Java_eu_modernmt_decoder_moses_MosesDecoder_getFeatures
  (JNIEnv *, jobject);

/*
 * Class:     eu_modernmt_decoder_moses_MosesDecoder
 * Method:    getFeatureWeights
 * Signature: (Leu/modernmt/decoder/moses/MosesFeature;)[F
 */
JNIEXPORT jfloatArray JNICALL Java_eu_modernmt_decoder_moses_MosesDecoder_getFeatureWeights
  (JNIEnv *, jobject, jobject);

/*
 * Class:     eu_modernmt_decoder_moses_MosesDecoder
 * Method:    createSession
 * Signature: (Ljava/util/Map;)J
 */
JNIEXPORT jlong JNICALL Java_eu_modernmt_decoder_moses_MosesDecoder_createSession
  (JNIEnv *, jobject, jobject);

/*
 * Class:     eu_modernmt_decoder_moses_MosesDecoder
 * Method:    destroySession
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_eu_modernmt_decoder_moses_MosesDecoder_destroySession
  (JNIEnv *, jobject, jlong);

/*
 * Class:     eu_modernmt_decoder_moses_MosesDecoder
 * Method:    translate
 * Signature: (Ljava/lang/String;Ljava/util/Map;JI)Leu/modernmt/decoder/moses/TranslationExchangeObject;
 */
JNIEXPORT jobject JNICALL Java_eu_modernmt_decoder_moses_MosesDecoder_translate
  (JNIEnv *, jobject, jstring, jobject, jlong, jint);

/*
 * Class:     eu_modernmt_decoder_moses_MosesDecoder
 * Method:    dispose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_eu_modernmt_decoder_moses_MosesDecoder_dispose
  (JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif
