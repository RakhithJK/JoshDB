/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class org_josh_JoshDB_FileTrie_MergeFile */

#ifndef _Included_org_josh_JoshDB_FileTrie_MergeFile
#define _Included_org_josh_JoshDB_FileTrie_MergeFile
#ifdef __cplusplus
extern "C" {
#endif
#undef org_josh_JoshDB_FileTrie_MergeFile_PIPE_BUF
#define org_josh_JoshDB_FileTrie_MergeFile_PIPE_BUF 4096L
/*
 * Class:     org_josh_JoshDB_FileTrie_MergeFile
 * Method:    openFile
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_org_josh_JoshDB_FileTrie_MergeFile_openFile
  (JNIEnv *, jclass, jstring);

/*
 * Class:     org_josh_JoshDB_FileTrie_MergeFile
 * Method:    closeFile
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_org_josh_JoshDB_FileTrie_MergeFile_closeFile
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_josh_JoshDB_FileTrie_MergeFile
 * Method:    appendToFile
 * Signature: (J[BJ)J
 */
JNIEXPORT jlong JNICALL Java_org_josh_JoshDB_FileTrie_MergeFile_appendToFile
  (JNIEnv *, jclass, jlong, jbyteArray, jlong);

#ifdef __cplusplus
}
#endif
#endif
