#ifndef BIGMATH_EXPORT_H
#define BIGMATH_EXPORT_H

#ifdef _WIN32
#define BIGMATH_EXPORT __declspec(dllexport)
#else
#define BIGMATH_EXPORT __attribute__((visibility("default")))
#endif

#endif /* BIGMATH_EXPORT_H */
