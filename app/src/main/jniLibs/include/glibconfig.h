#ifndef GLIBCONFIG_H
#define GLIBCONFIG_H

#include <limits.h>
#include <float.h>
#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// Version macros
#define GLIB_MAJOR_VERSION 2
#define GLIB_MINOR_VERSION 76
#define GLIB_MICRO_VERSION 0

#define G_MININT8   ((gint8)  0x80)
#define G_MAXINT8   ((gint8)  0x7f)
#define G_MAXUINT8  ((guint8) 0xff)

#define G_MININT16  ((gint16)  0x8000)
#define G_MAXINT16  ((gint16)  0x7fff)
#define G_MAXUINT16 ((guint16) 0xffff)

#define G_MININT32  ((gint32)  0x80000000)
#define G_MAXINT32  ((gint32)  0x7fffffff)
#define G_MAXUINT32 ((guint32) 0xffffffff)

#define G_MININT64  ((gint64)  0x8000000000000000LL)
#define G_MAXINT64  ((gint64)  0x7fffffffffffffffLL)
#define G_MAXUINT64 ((guint64) 0xffffffffffffffffULL)

#define G_MINSHORT  SHRT_MIN
#define G_MAXSHORT  SHRT_MAX
#define G_MAXUSHORT USHRT_MAX

#define G_MININT    INT_MIN
#define G_MAXINT    INT_MAX
#define G_MAXUINT   UINT_MAX

#define G_MINLONG   LONG_MIN
#define G_MAXLONG   LONG_MAX
#define G_MAXULONG  ULONG_MAX

// Endianness
#define G_LITTLE_ENDIAN 1234
#define G_BIG_ENDIAN    4321
#define G_PDP_ENDIAN    3412

#define G_BYTE_ORDER G_LITTLE_ENDIAN

// Basic types
typedef int8_t   gint8;
typedef uint8_t  guint8;
typedef int16_t  gint16;
typedef uint16_t guint16;
typedef int32_t  gint32;
typedef uint32_t guint32;
typedef int64_t  gint64;
typedef uint64_t guint64;

typedef int      gboolean;

// Size types
#if defined(__LP64__) || defined(_LP64)
typedef int64_t  gssize;
typedef uint64_t gsize;
#define G_GSIZE_MODIFIER "l"
#define G_GSSIZE_MODIFIER "l"
#define G_GSIZE_FORMAT "lu"
#define G_GSSIZE_FORMAT "ld"
#else
typedef int32_t  gssize;
typedef uint32_t gsize;
#define G_GSIZE_MODIFIER ""
#define G_GSSIZE_MODIFIER ""
#define G_GSIZE_FORMAT "u"
#define G_GSSIZE_FORMAT "d"
#endif

typedef int64_t goffset;
#define G_MINOFFSET G_MININT64
#define G_MAXOFFSET G_MAXINT64

// Format strings for 64-bit int
#define G_GINT64_MODIFIER "ll"
#define G_GINT64_FORMAT "lld"
#define G_GUINT64_FORMAT "llu"

// Type size constants
#define GLIB_SIZEOF_CHAR 1
#define GLIB_SIZEOF_SHORT 2
#define GLIB_SIZEOF_INT 4
#define GLIB_SIZEOF_LONG_LONG 8

#if defined(__LP64__) || defined(_LP64)
#define GLIB_SIZEOF_VOID_P 8
#define GLIB_SIZEOF_LONG 8
#define GLIB_SIZEOF_SIZE_T 8
#else
#define GLIB_SIZEOF_VOID_P 4
#define GLIB_SIZEOF_LONG 4
#define GLIB_SIZEOF_SIZE_T 4
#endif

// Pointer size integers
typedef uintptr_t guintptr;
typedef intptr_t gintptr;

// GPid definition
typedef int GPid;

// Sysdefs for poll (these don't have = in gmain.h)
#define GLIB_SYSDEF_POLLIN = 1
#define GLIB_SYSDEF_POLLOUT = 4
#define GLIB_SYSDEF_POLLPRI = 2
#define GLIB_SYSDEF_POLLERR = 8
#define GLIB_SYSDEF_POLLHUP = 16
#define GLIB_SYSDEF_POLLNVAL = 32

// Sysdefs for sockets (these have = in gioenums.h)
#define GLIB_SYSDEF_AF_UNIX 1
#define GLIB_SYSDEF_AF_INET 2
#define GLIB_SYSDEF_AF_INET6 10

#define GLIB_SYSDEF_MSG_OOB 1
#define GLIB_SYSDEF_MSG_PEEK 2
#define GLIB_SYSDEF_MSG_DONTROUTE 4

// Threads / mutex
#define G_THREADS_ENABLED
#define G_THREADS_IMPL_POSIX

// Standard inline
#ifndef G_INLINE_FUNC
#  ifdef __cplusplus
#    define G_INLINE_FUNC inline
#  else
#    define G_INLINE_FUNC static inline
#  endif
#endif

#ifdef __cplusplus
}
#endif

#endif /* GLIBCONFIG_H */
