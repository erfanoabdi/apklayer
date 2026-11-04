# apklayer: the board half of product.mk (Digitalis's docs/integrating-digitalis.md, step 2): Soong builds the native_bridge (arm64 guest)
# variants of bionic and the NDK libraries. Included through build/make/core/board_config.mk's vendor/extra/BoardConfigExtra.mk hook.
ifeq ($(APKLAYER_NATIVE_BRIDGE),arm64)
ifeq ($(TARGET_ARCH),x86_64)
TARGET_NATIVE_BRIDGE_ARCH := arm64
TARGET_NATIVE_BRIDGE_ARCH_VARIANT := armv8-a
TARGET_NATIVE_BRIDGE_CPU_VARIANT := generic
TARGET_NATIVE_BRIDGE_ABI := arm64-v8a
endif
endif
