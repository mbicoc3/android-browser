use adblock::engine::Engine;
use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_com_example_feature_shield_engine_NativeAdblockBridge_nativeCreateEngine(
    mut env: JNIEnv,
    _class: JClass,
    rules_text: JString,
) -> jlong {
    let input: String = match env.get_string(&rules_text) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let rules: Vec<String> = input.lines().map(|line| line.to_string()).collect();
    let mut filter_set = FilterSet::new(false);
    filter_set.add_filters(&rules, ParseOptions::default());
    let engine = Engine::from_filter_set(filter_set, true);
    Box::into_raw(Box::new(engine)) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_example_feature_shield_engine_NativeAdblockBridge_nativeShouldBlock(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
    source_url: JString,
    request_type: JString,
) -> jboolean {
    if handle == 0 {
        return JNI_FALSE;
    }
    let engine = unsafe { &*(handle as *const Engine) };
    let req_url: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return JNI_FALSE,
    };
    let src_url: String = env.get_string(&source_url).map(Into::into).unwrap_or_default();
    let req_type: String = env.get_string(&request_type).map(Into::into).unwrap_or_else(|_| "other".into());

    match Request::new(&req_url, &src_url, &req_type) {
        Ok(request) => {
            let result = engine.check_network_request(&request);
            if result.matched {
                JNI_TRUE
            } else {
                JNI_FALSE
            }
        }
        Err(_) => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_feature_shield_engine_NativeAdblockBridge_nativeCosmeticCss(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let engine = unsafe { &*(handle as *const Engine) };
    let page_url: String = match env.get_string(&url) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let resources = engine.url_cosmetic_resources(&page_url);
    if resources.hide_selectors.is_empty() {
        return env.new_string("").map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut());
    }
    let selectors: Vec<String> = resources.hide_selectors.into_iter().take(160).collect();
    let css = format!(
        "{} {{ display: none !important; visibility: hidden !important; }}",
        selectors.join(", ")
    );
    env.new_string(css)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_example_feature_shield_engine_NativeAdblockBridge_nativeDestroyEngine(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            drop(Box::from_raw(handle as *mut Engine));
        }
    }
}
