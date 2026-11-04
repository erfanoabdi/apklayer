# apklayer: Digitalis (the arm64 guest of AOSP's Berberis translator) in a gsi_x86_64 build, only when the build's environment has
# APKLAYER_NATIVE_BRIDGE=arm64 (docs/X86_64.md "arm64-only apps"). Included through LineageOS's vendor/extra/product.mk hook; without the variable
# the product is unchanged, so other builds of the tree are not affected.
# Only the build half of frameworks/libs/binary_translation/enable_arm64_to_x86_64.mk: its packages and ro.* properties would break the
# GSI's artifact path requirement, and apklayer stages the outputs itself (`m <module>` builds a module that is not in PRODUCT_PACKAGES).
ifeq ($(APKLAYER_NATIVE_BRIDGE),arm64)
include frameworks/libs/binary_translation/berberis_config.mk  # the module lists only (BERBERIS_PRODUCT_PACKAGES_ARM64_TO_X86_64, the artifacts)
PRODUCT_SOONG_NAMESPACES += frameworks/libs/native_bridge_support/android_api/libc
BUILD_BERBERIS := true
BUILD_BERBERIS_ARM64_TO_X86_64 := true
$(call soong_config_set,berberis,translation_arch,arm64_to_x86_64)
endif
