/* GCC before 12 cannot use C11 <stdatomic.h> from C++: bionic_translation's libstdc++_standalone/__cxa_guard.cpp does. The few calls it makes, through <atomic>. */
#pragma once
#include <atomic>
using std::atomic_int;
using std::memory_order_acquire;
using std::memory_order_release;
using std::memory_order_relaxed;
using std::memory_order_acq_rel;
using std::memory_order_seq_cst;
#define atomic_load_explicit(obj, order) std::atomic_load_explicit(obj, order)
#define atomic_exchange_explicit(obj, desired, order) std::atomic_exchange_explicit(obj, desired, order)
#define atomic_compare_exchange_weak_explicit(obj, expected, desired, success, failure) std::atomic_compare_exchange_weak_explicit(obj, expected, desired, success, failure)
