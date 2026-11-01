// Built with Android NDK 28.2.13676358 (android-ndk-r28c), CMake 3.22.1, minSdk 24 (arm64-v8a).
// Target Runtime Commits:
// - llama.cpp: mybigday/llama.rn v0.13.0-rc.9 (27783c86ce) / ggml-org/llama.cpp (0ec4027a2a)
// - Bitnet.cpp: microsoft/BitNet (0b341e582a) / isHuangXin/llama.cpp (390c307)
// - T-MAC: microsoft/T-MAC (7042f8f733) / kaleid-liner/llama.cpp (eb07ecf)

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <random>
#include <string>
#include <unordered_map>
#include <vector>
#include <algorithm>

#define LOG_TAG "LeafyNativeBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

typedef int32_t llama_pos;
typedef int32_t llama_token;
typedef int32_t llama_seq_id;
typedef bool (*llama_progress_callback)(float progress, void * user_data);
typedef bool (*ggml_backend_sched_eval_callback)(struct ggml_tensor * t, bool ask, void * user_data);
typedef bool (*ggml_abort_callback)(void * data);
typedef void (*ggml_log_callback)(int level, const char * text, void * user_data);

struct llama_chat_message {
    const char * role;
    const char * content;
};

enum ggml_type : int32_t {
    GGML_TYPE_F32 = 0,
    GGML_TYPE_F16 = 1,
};

enum llama_split_mode : int32_t {
    LLAMA_SPLIT_MODE_NONE  = 0,
    LLAMA_SPLIT_MODE_LAYER = 1,
    LLAMA_SPLIT_MODE_ROW   = 2,
};

enum llama_rope_scaling_type : int32_t {
    LLAMA_ROPE_SCALING_TYPE_UNSPECIFIED = -1,
    LLAMA_ROPE_SCALING_TYPE_NONE        = 0,
    LLAMA_ROPE_SCALING_TYPE_LINEAR      = 1,
    LLAMA_ROPE_SCALING_TYPE_YARN        = 2,
};

enum llama_pooling_type : int32_t {
    LLAMA_POOLING_TYPE_UNSPECIFIED = -1,
    LLAMA_POOLING_TYPE_NONE = 0,
};

enum llama_attention_type : int32_t {
    LLAMA_ATTENTION_TYPE_UNSPECIFIED = -1,
    LLAMA_ATTENTION_TYPE_CAUSAL = 0,
};

enum llama_flash_attn_type : int32_t {
    LLAMA_FLASH_ATTN_TYPE_AUTO     = -1,
    LLAMA_FLASH_ATTN_TYPE_DISABLED = 0,
    LLAMA_FLASH_ATTN_TYPE_ENABLED  = 1,
};

enum llama_context_type : int32_t {
    LLAMA_CONTEXT_TYPE_UNSPECIFIED = -1,
    LLAMA_CONTEXT_TYPE_CLASSIC     = 0,
};

enum llama_load_mode : int32_t {
    LLAMA_LOAD_MODE_AUTO       = -1,
    LLAMA_LOAD_MODE_NONE       =  0,
    LLAMA_LOAD_MODE_MMAP       =  1,
    LLAMA_LOAD_MODE_MLOCK      =  2,
    LLAMA_LOAD_MODE_MMAP_MLOCK =  3,
    LLAMA_LOAD_MODE_DIRECT_IO  =  4,
};

enum llama_lazy_mode : int32_t {
    LLAMA_LAZY_MODE_OFF  = 0,
    LLAMA_LAZY_MODE_AUTO = 1,
    LLAMA_LAZY_MODE_ON   = 2,
};

// ============================================================================
// 1. ABI definitions for llama.cpp v0.13.0-rc.9 (commit 0ec4027a2a)
// ============================================================================
namespace abi_v013 {
struct llama_model;
struct llama_context;
struct llama_vocab;
struct llama_memory_i;
typedef struct llama_memory_i * llama_memory_t;

struct llama_model_params {
    void * devices;
    const void * tensor_buft_overrides;
    int32_t n_gpu_layers;
    enum llama_split_mode split_mode;
    enum llama_load_mode  load_mode;
    enum llama_lazy_mode  lazy_mode;
    int32_t main_gpu;
    const float * tensor_split;
    llama_progress_callback progress_callback;
    void * progress_callback_user_data;
    const void * kv_overrides;
    bool vocab_only;
    bool check_tensors;
    bool use_extra_bufts;
    bool no_host;
    bool no_alloc;
    bool load_mtp;
};

struct llama_context_params {
    uint32_t n_ctx;
    uint32_t n_batch;
    uint32_t n_ubatch;
    uint32_t n_seq_max;
    uint32_t n_rs_seq;
    uint32_t n_outputs_max;
    int32_t  n_threads;
    int32_t  n_threads_batch;
    enum llama_context_type      ctx_type;
    enum llama_rope_scaling_type rope_scaling_type;
    enum llama_pooling_type      pooling_type;
    enum llama_attention_type    attention_type;
    enum llama_flash_attn_type   flash_attn_type;
    float    rope_freq_base;
    float    rope_freq_scale;
    float    yarn_ext_factor;
    float    yarn_attn_factor;
    float    yarn_beta_fast;
    float    yarn_beta_slow;
    uint32_t yarn_orig_ctx;
    float    defrag_thold;
    ggml_backend_sched_eval_callback cb_eval;
    void * cb_eval_user_data;
    enum ggml_type type_k;
    enum ggml_type type_v;
    size_t moe_cache_size;
    ggml_abort_callback abort_callback;
    void *              abort_callback_data;
    bool embeddings;
    bool offload_kqv;
    bool no_perf;
    bool op_offload;
    bool swa_full;
    bool kv_unified;
    void * samplers;
    size_t n_samplers;
    struct llama_context * ctx_other;
};

struct llama_batch {
    int32_t n_tokens;
    llama_token  *  token;
    float        *  embd;
    llama_pos    *  pos;
    int32_t      *  n_seq_id;
    llama_seq_id ** seq_id;
    int8_t       *  logits;
};
} // namespace abi_v013

// ============================================================================
// 2. ABI definitions for Bitnet.cpp (microsoft/BitNet 0b341e582a / llama.cpp 390c307)
// ============================================================================
namespace abi_bitnet {
struct llama_model;
struct llama_context;
struct llama_vocab;
struct llama_memory_i;
typedef struct llama_memory_i * llama_memory_t;

struct llama_model_params {
    void * devices;
    const void * tensor_buft_overrides;
    int32_t n_gpu_layers;
    enum llama_split_mode split_mode;
    int32_t main_gpu;
    const float * tensor_split;
    llama_progress_callback progress_callback;
    void * progress_callback_user_data;
    const void * kv_overrides;
    bool vocab_only;
    bool use_mmap;
    bool use_direct_io;
    bool use_mlock;
    bool check_tensors;
    bool use_extra_bufts;
    bool no_host;
    bool no_alloc;
};

struct llama_context_params {
    uint32_t n_ctx;
    uint32_t n_batch;
    uint32_t n_ubatch;
    uint32_t n_seq_max;
    uint32_t n_rs_seq;
    uint32_t n_outputs_max;
    int32_t  n_threads;
    int32_t  n_threads_batch;
    enum llama_context_type      ctx_type;
    enum llama_rope_scaling_type rope_scaling_type;
    enum llama_pooling_type      pooling_type;
    enum llama_attention_type    attention_type;
    enum llama_flash_attn_type   flash_attn_type;
    float    rope_freq_base;
    float    rope_freq_scale;
    float    yarn_ext_factor;
    float    yarn_attn_factor;
    float    yarn_beta_fast;
    float    yarn_beta_slow;
    uint32_t yarn_orig_ctx;
    float    defrag_thold;
    ggml_backend_sched_eval_callback cb_eval;
    void * cb_eval_user_data;
    enum ggml_type type_k;
    enum ggml_type type_v;
    ggml_abort_callback abort_callback;
    void *              abort_callback_data;
    bool embeddings;
    bool offload_kqv;
    bool no_perf;
    bool op_offload;
    bool swa_full;
    bool kv_unified;
    void * samplers;
    size_t n_samplers;
    struct llama_context * ctx_other;
};

struct llama_batch {
    int32_t n_tokens;
    llama_token  *  token;
    float        *  embd;
    llama_pos    *  pos;
    int32_t      *  n_seq_id;
    llama_seq_id ** seq_id;
    int8_t       *  logits;
};
} // namespace abi_bitnet

// ============================================================================
// 3. ABI definitions for T-MAC (microsoft/T-MAC 7042f8f733 / llama.cpp eb07ecf)
// ============================================================================
namespace abi_tmac {
struct llama_model;
struct llama_context;

struct llama_model_params {
    int32_t n_gpu_layers;
    enum llama_split_mode split_mode;
    int32_t main_gpu;
    const float * tensor_split;
    const char * rpc_servers;
    llama_progress_callback progress_callback;
    void * progress_callback_user_data;
    const void * kv_overrides;
    bool vocab_only;
    bool use_mmap;
    bool use_mlock;
    bool check_tensors;
};

struct llama_context_params {
    uint32_t n_ctx;
    uint32_t n_batch;
    uint32_t n_ubatch;
    uint32_t n_seq_max;
    int32_t  n_threads;
    int32_t  n_threads_batch;
    enum llama_rope_scaling_type rope_scaling_type;
    enum llama_pooling_type      pooling_type;
    enum llama_attention_type    attention_type;
    float    rope_freq_base;
    float    rope_freq_scale;
    float    yarn_ext_factor;
    float    yarn_attn_factor;
    float    yarn_beta_fast;
    float    yarn_beta_slow;
    uint32_t yarn_orig_ctx;
    float    defrag_thold;
    ggml_backend_sched_eval_callback cb_eval;
    void * cb_eval_user_data;
    enum ggml_type type_k;
    enum ggml_type type_v;
    bool logits_all;
    bool embeddings;
    bool offload_kqv;
    bool flash_attn;
    bool no_perf;
    ggml_abort_callback abort_callback;
    void *              abort_callback_data;
};

struct llama_batch {
    int32_t n_tokens;
    llama_token  *  token;
    float        *  embd;
    llama_pos    *  pos;
    int32_t      *  n_seq_id;
    llama_seq_id ** seq_id;
    int8_t       *  logits;
    llama_pos    all_pos_0;
    llama_pos    all_pos_1;
    llama_seq_id all_seq_id;
};
} // namespace abi_tmac

enum class RuntimeVariant {
    V013,
    BITNET,
    TMAC
};

std::mutex g_log_mutex;
std::string g_last_native_error_log;

void clear_native_error_log() {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    g_last_native_error_log.clear();
}

std::string get_native_error_log() {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    return g_last_native_error_log;
}

void native_llama_log_cb(int level, const char * text, void * /*user_data*/) {
    if (text == nullptr) return;
    // ggml_log_level: 2 = INFO, 3 = WARN, 4 = ERROR
    if (level >= 3) {
        LOGE("[native-llama] %s", text);
        std::lock_guard<std::mutex> lock(g_log_mutex);
        std::string line(text);
        while (!line.empty() && (line.back() == '\n' || line.back() == '\r')) {
            line.pop_back();
        }
        if (!line.empty()) {
            if (!g_last_native_error_log.empty()) {
                g_last_native_error_log += "; ";
            }
            g_last_native_error_log += line;
            if (g_last_native_error_log.size() > 600) {
                g_last_native_error_log = g_last_native_error_log.substr(g_last_native_error_log.size() - 600);
            }
        }
    }
}

void throw_runtime_exception(JNIEnv* env, const std::string& msg) {
    std::string detail = get_native_error_log();
    std::string full_msg = detail.empty() ? msg : (msg + " [Native diagnostic: " + detail + "]");
    LOGE("%s", full_msg.c_str());
    jclass exClass = env->FindClass("java/lang/RuntimeException");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, full_msg.c_str());
    }
}

// Helper to sample next token from logits with temperature, top-p, and repetition penalty
llama_token sample_next_token(
    const float* logits,
    int32_t n_vocab,
    float temperature,
    float top_p,
    float repeat_penalty,
    const std::vector<llama_token>& recent_tokens,
    std::mt19937& rng
) {
    if (logits == nullptr || n_vocab <= 0) {
        return 0;
    }

    std::vector<std::pair<float, llama_token>> candidates;
    candidates.reserve(n_vocab);
    for (int32_t id = 0; id < n_vocab; ++id) {
        float val = logits[id];
        if (!std::isfinite(val)) {
            val = -1e10f;
        }
        candidates.emplace_back(val, id);
    }

    const float eff_repeat_penalty = std::clamp(repeat_penalty, 1.0f, 2.0f);
    if (eff_repeat_penalty > 1.001f) {
        for (llama_token tok : recent_tokens) {
            if (tok >= 0 && tok < n_vocab) {
                float v = candidates[tok].first;
                candidates[tok].first = (v <= 0.0f) ? (v * eff_repeat_penalty) : (v / eff_repeat_penalty);
            }
        }
    }

    if (temperature <= 0.01f) {
        auto best = std::max_element(
            candidates.begin(),
            candidates.end(),
            [](const auto& a, const auto& b) { return a.first < b.first; }
        );
        return best->second;
    }

    const size_t top_k = std::min<size_t>(64, candidates.size());
    std::partial_sort(
        candidates.begin(),
        candidates.begin() + top_k,
        candidates.end(),
        [](const auto& a, const auto& b) { return a.first > b.first; }
    );
    candidates.resize(top_k);

    float max_logit = candidates[0].first;
    float sum_exp = 0.0f;
    for (size_t i = 0; i < top_k; ++i) {
        float p = std::exp((candidates[i].first - max_logit) / temperature);
        candidates[i].first = p;
        sum_exp += p;
    }
    if (sum_exp <= 0.0f) {
        return candidates[0].second;
    }

    float cum_prob = 0.0f;
    size_t cutoff = top_k;
    const float clamped_top_p = std::clamp(top_p, 0.05f, 1.0f);
    for (size_t i = 0; i < top_k; ++i) {
        candidates[i].first /= sum_exp;
        cum_prob += candidates[i].first;
        if (cum_prob >= clamped_top_p) {
            cutoff = i + 1;
            break;
        }
    }
    candidates.resize(cutoff);

    float renorm = 0.0f;
    for (const auto& c : candidates) {
        renorm += c.first;
    }
    std::uniform_real_distribution<float> dist(0.0f, renorm);
    float r = dist(rng);
    float acc = 0.0f;
    for (const auto& c : candidates) {
        acc += c.first;
        if (r <= acc) {
            return c.second;
        }
    }
    return candidates.back().second;
}

size_t valid_utf8_prefix_len(const std::string& buf) {
    size_t len = buf.size();
    if (len == 0) return 0;
    for (size_t back = 1; back <= 3 && back <= len; ++back) {
        unsigned char c = static_cast<unsigned char>(buf[len - back]);
        if ((c & 0x80) == 0x00) {
            return len;
        }
        if ((c & 0xC0) == 0xC0) {
            size_t expected_seq_len = 1;
            if ((c & 0xE0) == 0xC0) expected_seq_len = 2;
            else if ((c & 0xF0) == 0xE0) expected_seq_len = 3;
            else if ((c & 0xF8) == 0xF0) expected_seq_len = 4;
            if (back < expected_seq_len) {
                return len - back;
            }
            return len;
        }
    }
    return len;
}

struct EngineSession {
    RuntimeVariant variant;
    std::string so_name;
    void* dl_handle = nullptr;
    void* model = nullptr;
    void* ctx = nullptr;
    const void* vocab = nullptr;
    int32_t n_ctx = 2048;
    int32_t n_batch = 512;
    int32_t n_threads = 4;
    std::string model_desc;
    std::atomic<bool> cancel_requested{false};
    std::mutex infer_mutex;

    // Function pointers resolved via dlsym
    void (*fn_backend_init)(void) = nullptr;
    void (*fn_log_set)(ggml_log_callback, void*) = nullptr;
    void (*fn_set_n_threads)(void*, int32_t, int32_t) = nullptr;
    void (*fn_free_ctx)(void*) = nullptr;
    void (*fn_free_model)(void*) = nullptr;
    int32_t (*fn_model_desc)(const void*, char*, size_t) = nullptr;
    uint32_t (*fn_n_ctx)(const void*) = nullptr;
    float* (*fn_get_logits_ith)(void*, int32_t) = nullptr;

    // Vocab/Model specific pointers
    const void* (*fn_model_get_vocab)(const void*) = nullptr;
    int32_t (*fn_tokenize)(const void*, const char*, int32_t, llama_token*, int32_t, bool, bool) = nullptr;
    int32_t (*fn_token_to_piece)(const void*, llama_token, char*, int32_t, int32_t, bool) = nullptr;
    bool (*fn_is_eog)(const void*, llama_token) = nullptr;
    int32_t (*fn_n_vocab)(const void*) = nullptr;

    // Chat template pointers
    const char* (*fn_model_chat_template)(const void*, const char*) = nullptr;
    int32_t (*fn_chat_apply_template_v013)(const char*, const llama_chat_message*, size_t, bool, char*, int32_t) = nullptr;
    int32_t (*fn_chat_apply_template_tmac)(const void*, const char*, const llama_chat_message*, size_t, bool, char*, int32_t) = nullptr;

    // Memory / KV cache clearing
    void* (*fn_get_memory)(const void*) = nullptr;
    void (*fn_memory_clear)(void*, bool) = nullptr;
    void (*fn_kv_cache_clear)(void*) = nullptr;

    // Variant-specific batch & decode pointers
    abi_v013::llama_batch (*fn_batch_init_v013)(int32_t, int32_t, int32_t) = nullptr;
    void (*fn_batch_free_v013)(abi_v013::llama_batch) = nullptr;
    int32_t (*fn_decode_v013)(void*, abi_v013::llama_batch) = nullptr;

    abi_tmac::llama_batch (*fn_batch_init_tmac)(int32_t, int32_t, int32_t) = nullptr;
    void (*fn_batch_free_tmac)(abi_tmac::llama_batch) = nullptr;
    int32_t (*fn_decode_tmac)(void*, abi_tmac::llama_batch) = nullptr;

    ~EngineSession() {
        if (ctx != nullptr && fn_free_ctx != nullptr) {
            fn_free_ctx(ctx);
            ctx = nullptr;
        }
        if (model != nullptr && fn_free_model != nullptr) {
            fn_free_model(model);
            model = nullptr;
        }
    }
};

std::mutex g_sessions_mutex;
std::unordered_map<jlong, std::shared_ptr<EngineSession>> g_sessions;
std::atomic<jlong> g_next_handle{1};

bool abort_cb_trampoline(void* user_data) {
    auto* session = static_cast<EngineSession*>(user_data);
    return session != nullptr && session->cancel_requested.load(std::memory_order_relaxed);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_engine_LeafyNativeBridge_nativeInitContext(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring j_so_name,
    jstring j_model_path,
    jint n_ctx,
    jint n_batch,
    jint n_threads,
    jboolean use_mmap
) {
    clear_native_error_log();
    if (j_so_name == nullptr || j_model_path == nullptr) {
        throw_runtime_exception(env, "nativeInitContext received null library name or model path.");
        return 0;
    }

    const char* c_so_name = env->GetStringUTFChars(j_so_name, nullptr);
    const char* c_model_path = env->GetStringUTFChars(j_model_path, nullptr);
    std::string so_name(c_so_name ? c_so_name : "");
    std::string model_path(c_model_path ? c_model_path : "");
    if (c_so_name) env->ReleaseStringUTFChars(j_so_name, c_so_name);
    if (c_model_path) env->ReleaseStringUTFChars(j_model_path, c_model_path);

    dlerror();
    void* dl_handle = dlopen(so_name.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (dl_handle == nullptr) {
        const char* err = dlerror();
        throw_runtime_exception(
            env,
            "dlopen failed for '" + so_name + "': " + (err ? err : "unknown linker error")
        );
        return 0;
    }

    auto session = std::make_shared<EngineSession>();
    session->so_name = so_name;
    session->dl_handle = dl_handle;
    session->n_ctx = std::max<int32_t>(512, n_ctx);
    session->n_batch = std::max<int32_t>(64, n_batch);
    session->n_threads = std::max<int32_t>(1, n_threads);

    if (so_name.find("tmac") != std::string::npos) {
        session->variant = RuntimeVariant::TMAC;
    } else if (so_name.find("bitnet") != std::string::npos) {
        session->variant = RuntimeVariant::BITNET;
    } else {
        session->variant = RuntimeVariant::V013;
    }

    session->fn_log_set = reinterpret_cast<void (*)(ggml_log_callback, void*)>(dlsym(dl_handle, "llama_log_set"));
    if (session->fn_log_set != nullptr) {
        session->fn_log_set(native_llama_log_cb, nullptr);
    }

    session->fn_backend_init = reinterpret_cast<void (*)(void)>(dlsym(dl_handle, "llama_backend_init"));
    if (session->fn_backend_init == nullptr) {
        throw_runtime_exception(env, "dlsym(llama_backend_init) failed in " + so_name);
        return 0;
    }
    session->fn_backend_init();

    session->fn_set_n_threads = reinterpret_cast<void (*)(void*, int32_t, int32_t)>(dlsym(dl_handle, "llama_set_n_threads"));
    session->fn_free_ctx = reinterpret_cast<void (*)(void*)>(dlsym(dl_handle, "llama_free"));
    session->fn_model_desc = reinterpret_cast<int32_t (*)(const void*, char*, size_t)>(dlsym(dl_handle, "llama_model_desc"));
    session->fn_n_ctx = reinterpret_cast<uint32_t (*)(const void*)>(dlsym(dl_handle, "llama_n_ctx"));
    session->fn_get_logits_ith = reinterpret_cast<float* (*)(void*, int32_t)>(dlsym(dl_handle, "llama_get_logits_ith"));
    session->fn_tokenize = reinterpret_cast<int32_t (*)(const void*, const char*, int32_t, llama_token*, int32_t, bool, bool)>(dlsym(dl_handle, "llama_tokenize"));
    session->fn_token_to_piece = reinterpret_cast<int32_t (*)(const void*, llama_token, char*, int32_t, int32_t, bool)>(dlsym(dl_handle, "llama_token_to_piece"));

    if (!session->fn_free_ctx || !session->fn_get_logits_ith || !session->fn_tokenize || !session->fn_token_to_piece) {
        throw_runtime_exception(env, "Missing core llama_* symbols in " + so_name);
        return 0;
    }

    if (session->variant == RuntimeVariant::V013) {
        auto fn_model_default = reinterpret_cast<abi_v013::llama_model_params (*)(void)>(dlsym(dl_handle, "llama_model_default_params"));
        auto fn_ctx_default = reinterpret_cast<abi_v013::llama_context_params (*)(void)>(dlsym(dl_handle, "llama_context_default_params"));
        auto fn_load_model = reinterpret_cast<void* (*)(const char*, abi_v013::llama_model_params)>(dlsym(dl_handle, "llama_model_load_from_file"));
        auto fn_init_ctx = reinterpret_cast<void* (*)(void*, abi_v013::llama_context_params)>(dlsym(dl_handle, "llama_init_from_model"));

        session->fn_free_model = reinterpret_cast<void (*)(void*)>(dlsym(dl_handle, "llama_model_free"));
        session->fn_model_get_vocab = reinterpret_cast<const void* (*)(const void*)>(dlsym(dl_handle, "llama_model_get_vocab"));
        session->fn_is_eog = reinterpret_cast<bool (*)(const void*, llama_token)>(dlsym(dl_handle, "llama_vocab_is_eog"));
        session->fn_n_vocab = reinterpret_cast<int32_t (*)(const void*)>(dlsym(dl_handle, "llama_vocab_n_tokens"));
        session->fn_model_chat_template = reinterpret_cast<const char* (*)(const void*, const char*)>(dlsym(dl_handle, "llama_model_chat_template"));
        session->fn_chat_apply_template_v013 = reinterpret_cast<int32_t (*)(const char*, const llama_chat_message*, size_t, bool, char*, int32_t)>(dlsym(dl_handle, "llama_chat_apply_template"));
        session->fn_get_memory = reinterpret_cast<void* (*)(const void*)>(dlsym(dl_handle, "llama_get_memory"));
        session->fn_memory_clear = reinterpret_cast<void (*)(void*, bool)>(dlsym(dl_handle, "llama_memory_clear"));
        session->fn_batch_init_v013 = reinterpret_cast<abi_v013::llama_batch (*)(int32_t, int32_t, int32_t)>(dlsym(dl_handle, "llama_batch_init"));
        session->fn_batch_free_v013 = reinterpret_cast<void (*)(abi_v013::llama_batch)>(dlsym(dl_handle, "llama_batch_free"));
        session->fn_decode_v013 = reinterpret_cast<int32_t (*)(void*, abi_v013::llama_batch)>(dlsym(dl_handle, "llama_decode"));

        if (!fn_model_default || !fn_ctx_default || !fn_load_model || !fn_init_ctx ||
            !session->fn_free_model || !session->fn_model_get_vocab || !session->fn_is_eog ||
            !session->fn_n_vocab || !session->fn_batch_init_v013 || !session->fn_batch_free_v013 || !session->fn_decode_v013) {
            throw_runtime_exception(env, "Missing v0.13.0-rc.9 symbols in " + so_name);
            return 0;
        }

        abi_v013::llama_model_params mparams = fn_model_default();
        mparams.n_gpu_layers = 0;
        mparams.load_mode = use_mmap ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;
        session->model = fn_load_model(model_path.c_str(), mparams);
        if (session->model == nullptr) {
            throw_runtime_exception(
                env,
                "llama_model_load_from_file failed in " + so_name + " for model '" + model_path + "'."
            );
            return 0;
        }

        session->vocab = session->fn_model_get_vocab(session->model);
        abi_v013::llama_context_params cparams = fn_ctx_default();
        cparams.n_ctx = static_cast<uint32_t>(session->n_ctx);
        cparams.n_batch = static_cast<uint32_t>(session->n_batch);
        cparams.n_ubatch = static_cast<uint32_t>(session->n_batch);
        cparams.n_threads = session->n_threads;
        cparams.n_threads_batch = session->n_threads;
        cparams.abort_callback = abort_cb_trampoline;
        cparams.abort_callback_data = session.get();

        session->ctx = fn_init_ctx(session->model, cparams);
        if (session->ctx == nullptr) {
            throw_runtime_exception(env, "llama_init_from_model failed in " + so_name);
            return 0;
        }
    } else if (session->variant == RuntimeVariant::BITNET) {
        auto fn_model_default = reinterpret_cast<abi_bitnet::llama_model_params (*)(void)>(dlsym(dl_handle, "llama_model_default_params"));
        auto fn_ctx_default = reinterpret_cast<abi_bitnet::llama_context_params (*)(void)>(dlsym(dl_handle, "llama_context_default_params"));
        auto fn_load_model = reinterpret_cast<void* (*)(const char*, abi_bitnet::llama_model_params)>(dlsym(dl_handle, "llama_model_load_from_file"));
        auto fn_init_ctx = reinterpret_cast<void* (*)(void*, abi_bitnet::llama_context_params)>(dlsym(dl_handle, "llama_init_from_model"));

        session->fn_free_model = reinterpret_cast<void (*)(void*)>(dlsym(dl_handle, "llama_model_free"));
        session->fn_model_get_vocab = reinterpret_cast<const void* (*)(const void*)>(dlsym(dl_handle, "llama_model_get_vocab"));
        session->fn_is_eog = reinterpret_cast<bool (*)(const void*, llama_token)>(dlsym(dl_handle, "llama_vocab_is_eog"));
        session->fn_n_vocab = reinterpret_cast<int32_t (*)(const void*)>(dlsym(dl_handle, "llama_vocab_n_tokens"));
        session->fn_model_chat_template = reinterpret_cast<const char* (*)(const void*, const char*)>(dlsym(dl_handle, "llama_model_chat_template"));
        session->fn_chat_apply_template_v013 = reinterpret_cast<int32_t (*)(const char*, const llama_chat_message*, size_t, bool, char*, int32_t)>(dlsym(dl_handle, "llama_chat_apply_template"));
        session->fn_get_memory = reinterpret_cast<void* (*)(const void*)>(dlsym(dl_handle, "llama_get_memory"));
        session->fn_memory_clear = reinterpret_cast<void (*)(void*, bool)>(dlsym(dl_handle, "llama_memory_clear"));
        session->fn_batch_init_v013 = reinterpret_cast<abi_v013::llama_batch (*)(int32_t, int32_t, int32_t)>(dlsym(dl_handle, "llama_batch_init"));
        session->fn_batch_free_v013 = reinterpret_cast<void (*)(abi_v013::llama_batch)>(dlsym(dl_handle, "llama_batch_free"));
        session->fn_decode_v013 = reinterpret_cast<int32_t (*)(void*, abi_v013::llama_batch)>(dlsym(dl_handle, "llama_decode"));

        if (!fn_model_default || !fn_ctx_default || !fn_load_model || !fn_init_ctx ||
            !session->fn_free_model || !session->fn_model_get_vocab || !session->fn_is_eog ||
            !session->fn_n_vocab || !session->fn_batch_init_v013 || !session->fn_batch_free_v013 || !session->fn_decode_v013) {
            throw_runtime_exception(env, "Missing Bitnet.cpp symbols in " + so_name);
            return 0;
        }

        abi_bitnet::llama_model_params mparams = fn_model_default();
        mparams.n_gpu_layers = 0;
        mparams.use_mmap = static_cast<bool>(use_mmap);
        session->model = fn_load_model(model_path.c_str(), mparams);
        if (session->model == nullptr) {
            throw_runtime_exception(
                env,
                "Bitnet.cpp llama_model_load_from_file failed in " + so_name +
                " for '" + model_path + "' (Bitnet.cpp commit 0b341e582a / llama.cpp 390c307)."
            );
            return 0;
        }

        session->vocab = session->fn_model_get_vocab(session->model);
        abi_bitnet::llama_context_params cparams = fn_ctx_default();
        cparams.n_ctx = static_cast<uint32_t>(session->n_ctx);
        cparams.n_batch = static_cast<uint32_t>(session->n_batch);
        cparams.n_ubatch = static_cast<uint32_t>(session->n_batch);
        cparams.n_threads = session->n_threads;
        cparams.n_threads_batch = session->n_threads;
        cparams.abort_callback = abort_cb_trampoline;
        cparams.abort_callback_data = session.get();

        session->ctx = fn_init_ctx(session->model, cparams);
        if (session->ctx == nullptr) {
            throw_runtime_exception(env, "Bitnet.cpp llama_init_from_model failed in " + so_name);
            return 0;
        }
    } else {
        // RuntimeVariant::TMAC (eb07ecf)
        auto fn_model_default = reinterpret_cast<abi_tmac::llama_model_params (*)(void)>(dlsym(dl_handle, "llama_model_default_params"));
        auto fn_ctx_default = reinterpret_cast<abi_tmac::llama_context_params (*)(void)>(dlsym(dl_handle, "llama_context_default_params"));
        auto fn_load_model = reinterpret_cast<void* (*)(const char*, abi_tmac::llama_model_params)>(dlsym(dl_handle, "llama_load_model_from_file"));
        auto fn_init_ctx = reinterpret_cast<void* (*)(void*, abi_tmac::llama_context_params)>(dlsym(dl_handle, "llama_new_context_with_model"));

        session->fn_free_model = reinterpret_cast<void (*)(void*)>(dlsym(dl_handle, "llama_free_model"));
        session->fn_is_eog = reinterpret_cast<bool (*)(const void*, llama_token)>(dlsym(dl_handle, "llama_token_is_eog"));
        session->fn_n_vocab = reinterpret_cast<int32_t (*)(const void*)>(dlsym(dl_handle, "llama_n_vocab"));
        session->fn_chat_apply_template_tmac = reinterpret_cast<int32_t (*)(const void*, const char*, const llama_chat_message*, size_t, bool, char*, int32_t)>(dlsym(dl_handle, "llama_chat_apply_template"));
        session->fn_kv_cache_clear = reinterpret_cast<void (*)(void*)>(dlsym(dl_handle, "llama_kv_cache_clear"));
        session->fn_batch_init_tmac = reinterpret_cast<abi_tmac::llama_batch (*)(int32_t, int32_t, int32_t)>(dlsym(dl_handle, "llama_batch_init"));
        session->fn_batch_free_tmac = reinterpret_cast<void (*)(abi_tmac::llama_batch)>(dlsym(dl_handle, "llama_batch_free"));
        session->fn_decode_tmac = reinterpret_cast<int32_t (*)(void*, abi_tmac::llama_batch)>(dlsym(dl_handle, "llama_decode"));

        if (!fn_model_default || !fn_ctx_default || !fn_load_model || !fn_init_ctx ||
            !session->fn_free_model || !session->fn_is_eog || !session->fn_n_vocab ||
            !session->fn_batch_init_tmac || !session->fn_batch_free_tmac || !session->fn_decode_tmac) {
            throw_runtime_exception(env, "Missing T-MAC symbols in " + so_name);
            return 0;
        }

        abi_tmac::llama_model_params mparams = fn_model_default();
        mparams.n_gpu_layers = 0;
        mparams.use_mmap = static_cast<bool>(use_mmap);
        session->model = fn_load_model(model_path.c_str(), mparams);
        if (session->model == nullptr) {
            throw_runtime_exception(
                env,
                "T-MAC llama_load_model_from_file failed in " + so_name +
                " for '" + model_path + "' (T-MAC commit 7042f8f733 / llama.cpp eb07ecf)."
            );
            return 0;
        }

        session->vocab = session->model;
        abi_tmac::llama_context_params cparams = fn_ctx_default();
        cparams.n_ctx = static_cast<uint32_t>(session->n_ctx);
        cparams.n_batch = static_cast<uint32_t>(session->n_batch);
        cparams.n_ubatch = static_cast<uint32_t>(session->n_batch);
        cparams.n_threads = session->n_threads;
        cparams.n_threads_batch = session->n_threads;
        cparams.abort_callback = abort_cb_trampoline;
        cparams.abort_callback_data = session.get();

        session->ctx = fn_init_ctx(session->model, cparams);
        if (session->ctx == nullptr) {
            throw_runtime_exception(env, "T-MAC llama_new_context_with_model failed in " + so_name);
            return 0;
        }
    }

    if (session->fn_model_desc != nullptr) {
        char desc_buf[256] = {0};
        int32_t len = session->fn_model_desc(session->model, desc_buf, sizeof(desc_buf));
        if (len > 0) {
            session->model_desc.assign(desc_buf, std::min<size_t>(len, sizeof(desc_buf) - 1));
        }
    }
    if (session->fn_n_ctx != nullptr) {
        uint32_t actual_ctx = session->fn_n_ctx(session->ctx);
        if (actual_ctx > 0) {
            session->n_ctx = static_cast<int32_t>(actual_ctx);
        }
    }

    jlong handle_id = g_next_handle.fetch_add(1, std::memory_order_relaxed);
    {
        std::lock_guard<std::mutex> lock(g_sessions_mutex);
        g_sessions[handle_id] = session;
    }
    LOGI("Initialized native session handle=%lld via %s (desc='%s', n_ctx=%d)",
         static_cast<long long>(handle_id), so_name.c_str(), session->model_desc.c_str(), session->n_ctx);
    return handle_id;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_engine_LeafyNativeBridge_nativeGetModelDesc(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle_id
) {
    std::shared_ptr<EngineSession> session;
    {
        std::lock_guard<std::mutex> lock(g_sessions_mutex);
        auto it = g_sessions.find(handle_id);
        if (it != g_sessions.end()) {
            session = it->second;
        }
    }
    if (!session) {
        return env->NewStringUTF("unknown");
    }
    return env->NewStringUTF(session->model_desc.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_engine_LeafyNativeBridge_nativeGetContextSize(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle_id
) {
    std::shared_ptr<EngineSession> session;
    {
        std::lock_guard<std::mutex> lock(g_sessions_mutex);
        auto it = g_sessions.find(handle_id);
        if (it != g_sessions.end()) {
            session = it->second;
        }
    }
    return session ? session->n_ctx : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_engine_LeafyNativeBridge_nativeTokenizeCount(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle_id,
    jstring j_text
) {
    if (j_text == nullptr) return 0;
    std::shared_ptr<EngineSession> session;
    {
        std::lock_guard<std::mutex> lock(g_sessions_mutex);
        auto it = g_sessions.find(handle_id);
        if (it != g_sessions.end()) {
            session = it->second;
        }
    }
    if (!session || !session->fn_tokenize || !session->vocab) {
        return 0;
    }
    const char* c_text = env->GetStringUTFChars(j_text, nullptr);
    std::string text(c_text ? c_text : "");
    if (c_text) env->ReleaseStringUTFChars(j_text, c_text);
    if (text.empty()) return 0;

    int32_t n = session->fn_tokenize(
        session->vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        nullptr,
        0,
        true,
        true
    );
    if (n < 0) {
        return -n;
    }
    return n;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_engine_LeafyNativeBridge_nativeApplyChatTemplate(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle_id,
    jobjectArray j_roles,
    jobjectArray j_contents,
    jboolean add_assistant_prefix
) {
    if (j_roles == nullptr || j_contents == nullptr) {
        return env->NewStringUTF("");
    }
    std::shared_ptr<EngineSession> session;
    {
        std::lock_guard<std::mutex> lock(g_sessions_mutex);
        auto it = g_sessions.find(handle_id);
        if (it != g_sessions.end()) {
            session = it->second;
        }
    }
    if (!session || session->model == nullptr) {
        return env->NewStringUTF("");
    }

    jsize n_roles = env->GetArrayLength(j_roles);
    jsize n_contents = env->GetArrayLength(j_contents);
    jsize count = std::min(n_roles, n_contents);
    if (count <= 0) {
        return env->NewStringUTF("");
    }

    std::vector<std::string> role_strs(count);
    std::vector<std::string> content_strs(count);
    std::vector<llama_chat_message> msgs(count);
    for (jsize i = 0; i < count; ++i) {
        auto jr = static_cast<jstring>(env->GetObjectArrayElement(j_roles, i));
        auto jc = static_cast<jstring>(env->GetObjectArrayElement(j_contents, i));
        if (jr != nullptr) {
            const char* cr = env->GetStringUTFChars(jr, nullptr);
            if (cr) role_strs[i] = cr;
            if (cr) env->ReleaseStringUTFChars(jr, cr);
            env->DeleteLocalRef(jr);
        }
        if (jc != nullptr) {
            const char* cc = env->GetStringUTFChars(jc, nullptr);
            if (cc) content_strs[i] = cc;
            if (cc) env->ReleaseStringUTFChars(jc, cc);
            env->DeleteLocalRef(jc);
        }
        msgs[i].role = role_strs[i].c_str();
        msgs[i].content = content_strs[i].c_str();
    }

    std::vector<char> buf(4096);
    int32_t res_len = -1;
    if (session->variant == RuntimeVariant::TMAC) {
        if (session->fn_chat_apply_template_tmac != nullptr) {
            res_len = session->fn_chat_apply_template_tmac(
                session->model,
                nullptr,
                msgs.data(),
                static_cast<size_t>(count),
                static_cast<bool>(add_assistant_prefix),
                buf.data(),
                static_cast<int32_t>(buf.size())
            );
            if (res_len > static_cast<int32_t>(buf.size()) && res_len < 65536) {
                buf.resize(res_len + 16);
                res_len = session->fn_chat_apply_template_tmac(
                    session->model,
                    nullptr,
                    msgs.data(),
                    static_cast<size_t>(count),
                    static_cast<bool>(add_assistant_prefix),
                    buf.data(),
                    static_cast<int32_t>(buf.size())
                );
            }
        }
    } else {
        if (session->fn_model_chat_template != nullptr && session->fn_chat_apply_template_v013 != nullptr) {
            const char* tmpl = session->fn_model_chat_template(session->model, nullptr);
            if (tmpl != nullptr && std::strlen(tmpl) > 0) {
                res_len = session->fn_chat_apply_template_v013(
                    tmpl,
                    msgs.data(),
                    static_cast<size_t>(count),
                    static_cast<bool>(add_assistant_prefix),
                    buf.data(),
                    static_cast<int32_t>(buf.size())
                );
                if (res_len > static_cast<int32_t>(buf.size()) && res_len < 65536) {
                    buf.resize(res_len + 16);
                    res_len = session->fn_chat_apply_template_v013(
                        tmpl,
                        msgs.data(),
                        static_cast<size_t>(count),
                        static_cast<bool>(add_assistant_prefix),
                        buf.data(),
                        static_cast<int32_t>(buf.size())
                    );
                }
            }
        }
    }

    if (res_len > 0 && res_len <= static_cast<int32_t>(buf.size())) {
        std::string formatted(buf.data(), res_len);
        return env->NewStringUTF(formatted.c_str());
    }
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_LeafyNativeBridge_nativeRequestCancel(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle_id
) {
    std::shared_ptr<EngineSession> session;
    {
        std::lock_guard<std::mutex> lock(g_sessions_mutex);
        auto it = g_sessions.find(handle_id);
        if (it != g_sessions.end()) {
            session = it->second;
        }
    }
    if (session) {
        session->cancel_requested.store(true, std::memory_order_relaxed);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_LeafyNativeBridge_nativeFreeContext(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle_id
) {
    std::shared_ptr<EngineSession> session;
    {
        std::lock_guard<std::mutex> lock(g_sessions_mutex);
        auto it = g_sessions.find(handle_id);
        if (it != g_sessions.end()) {
            session = it->second;
            g_sessions.erase(it);
        }
    }
    if (session) {
        session->cancel_requested.store(true, std::memory_order_relaxed);
        std::lock_guard<std::mutex> infer_lock(session->infer_mutex);
    }
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_example_engine_LeafyNativeBridge_nativeComplete(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle_id,
    jstring j_prompt,
    jint n_predict,
    jint n_threads,
    jfloat temperature,
    jfloat top_p,
    jfloat repeat_penalty,
    jobjectArray j_stop_words,
    jobject j_callback
) {
    clear_native_error_log();
    std::shared_ptr<EngineSession> session;
    {
        std::lock_guard<std::mutex> lock(g_sessions_mutex);
        auto it = g_sessions.find(handle_id);
        if (it != g_sessions.end()) {
            session = it->second;
        }
    }
    if (!session || session->ctx == nullptr || session->model == nullptr) {
        throw_runtime_exception(env, "nativeComplete called with invalid or freed context handle.");
        return nullptr;
    }

    std::lock_guard<std::mutex> infer_lock(session->infer_mutex);
    session->cancel_requested.store(false, std::memory_order_relaxed);

    if (j_prompt == nullptr) {
        throw_runtime_exception(env, "nativeComplete received null prompt.");
        return nullptr;
    }

    const int32_t eff_threads = std::clamp<int32_t>(n_threads > 0 ? n_threads : session->n_threads, 1, 8);
    if (session->fn_set_n_threads != nullptr) {
        session->fn_set_n_threads(session->ctx, eff_threads, eff_threads);
    }

    const char* c_prompt = env->GetStringUTFChars(j_prompt, nullptr);
    std::string prompt(c_prompt ? c_prompt : "");
    if (c_prompt) env->ReleaseStringUTFChars(j_prompt, c_prompt);

    std::vector<std::string> stop_words;
    if (j_stop_words != nullptr) {
        jsize stop_count = env->GetArrayLength(j_stop_words);
        for (jsize i = 0; i < stop_count; ++i) {
            auto j_sw = static_cast<jstring>(env->GetObjectArrayElement(j_stop_words, i));
            if (j_sw != nullptr) {
                const char* c_sw = env->GetStringUTFChars(j_sw, nullptr);
                if (c_sw != nullptr && std::strlen(c_sw) > 0) {
                    stop_words.emplace_back(c_sw);
                }
                if (c_sw) env->ReleaseStringUTFChars(j_sw, c_sw);
                env->DeleteLocalRef(j_sw);
            }
        }
    }

    jclass cb_class = (j_callback != nullptr) ? env->GetObjectClass(j_callback) : nullptr;
    jmethodID on_piece_mid = (cb_class != nullptr)
        ? env->GetMethodID(cb_class, "onTokenPiece", "([B)V")
        : nullptr;
    if (cb_class != nullptr) {
        env->DeleteLocalRef(cb_class);
    }

    if (session->fn_get_memory != nullptr && session->fn_memory_clear != nullptr) {
        void* mem = session->fn_get_memory(session->ctx);
        if (mem != nullptr) {
            session->fn_memory_clear(mem, true);
        }
    } else if (session->fn_kv_cache_clear != nullptr) {
        session->fn_kv_cache_clear(session->ctx);
    }

    int32_t max_toks = static_cast<int32_t>(prompt.size()) + 16;
    std::vector<llama_token> prompt_tokens(max_toks);
    int32_t n_prompt = session->fn_tokenize(
        session->vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        prompt_tokens.data(),
        max_toks,
        true,
        true
    );
    if (n_prompt < 0) {
        max_toks = -n_prompt;
        prompt_tokens.resize(max_toks);
        n_prompt = session->fn_tokenize(
            session->vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            prompt_tokens.data(),
            max_toks,
            true,
            true
        );
    }
    if (n_prompt <= 0) {
        throw_runtime_exception(env, "llama_tokenize failed on prompt (returned " + std::to_string(n_prompt) + ").");
        return nullptr;
    }
    prompt_tokens.resize(n_prompt);

    const int32_t max_prompt_Allowed = std::max<int32_t>(64, session->n_ctx - std::min<int32_t>(n_predict, 512) - 4);
    if (n_prompt > max_prompt_Allowed) {
        prompt_tokens.erase(prompt_tokens.begin(), prompt_tokens.end() - max_prompt_Allowed);
        n_prompt = static_cast<int32_t>(prompt_tokens.size());
    }

    const int32_t batch_cap = std::max<int32_t>(64, session->n_batch);
    const int32_t n_vocab = session->fn_n_vocab(session->vocab);

    auto prefill_start = std::chrono::steady_clock::now();

    if (session->variant == RuntimeVariant::TMAC) {
        abi_tmac::llama_batch batch = session->fn_batch_init_tmac(batch_cap, 0, 1);
        for (int32_t base = 0; base < n_prompt; base += batch_cap) {
            if (session->cancel_requested.load(std::memory_order_relaxed)) {
                session->fn_batch_free_tmac(batch);
                break;
            }
            int32_t chunk = std::min<int32_t>(batch_cap, n_prompt - base);
            batch.n_tokens = chunk;
            for (int32_t i = 0; i < chunk; ++i) {
                batch.token[i] = prompt_tokens[base + i];
                batch.pos[i] = base + i;
                batch.n_seq_id[i] = 1;
                batch.seq_id[i][0] = 0;
                batch.logits[i] = (base + i == n_prompt - 1) ? 1 : 0;
            }
            int32_t rc = session->fn_decode_tmac(session->ctx, batch);
            if (rc != 0) {
                session->fn_batch_free_tmac(batch);
                if (session->cancel_requested.load(std::memory_order_relaxed)) {
                    break;
                }
                throw_runtime_exception(env, "llama_decode failed during prefill (rc=" + std::to_string(rc) + ").");
                return nullptr;
            }
        }
        session->fn_batch_free_tmac(batch);
    } else {
        abi_v013::llama_batch batch = session->fn_batch_init_v013(batch_cap, 0, 1);
        for (int32_t base = 0; base < n_prompt; base += batch_cap) {
            if (session->cancel_requested.load(std::memory_order_relaxed)) {
                session->fn_batch_free_v013(batch);
                break;
            }
            int32_t chunk = std::min<int32_t>(batch_cap, n_prompt - base);
            batch.n_tokens = chunk;
            for (int32_t i = 0; i < chunk; ++i) {
                batch.token[i] = prompt_tokens[base + i];
                batch.pos[i] = base + i;
                batch.n_seq_id[i] = 1;
                batch.seq_id[i][0] = 0;
                batch.logits[i] = (base + i == n_prompt - 1) ? 1 : 0;
            }
            int32_t rc = session->fn_decode_v013(session->ctx, batch);
            if (rc != 0) {
                session->fn_batch_free_v013(batch);
                if (session->cancel_requested.load(std::memory_order_relaxed)) {
                    break;
                }
                throw_runtime_exception(env, "llama_decode failed during prefill (rc=" + std::to_string(rc) + ").");
                return nullptr;
            }
        }
        session->fn_batch_free_v013(batch);
    }

    auto prefill_end = std::chrono::steady_clock::now();
    int64_t prefill_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(prefill_end - prefill_start).count();

    auto decode_start = std::chrono::steady_clock::now();
    int64_t generated_tokens = 0;
    int32_t n_past = n_prompt;
    int32_t last_logits_idx = (n_prompt - 1) % batch_cap;

    std::mt19937 rng(1337u);
    std::vector<llama_token> recent_tokens;
    recent_tokens.reserve(64);
    for (int32_t i = std::max<int32_t>(0, n_prompt - 64); i < n_prompt; ++i) {
        recent_tokens.push_back(prompt_tokens[i]);
    }

    std::string accumulated_text;
    std::string utf8_pending;

    abi_v013::llama_batch single_v013{};
    abi_tmac::llama_batch single_tmac{};
    if (session->variant == RuntimeVariant::TMAC) {
        single_tmac = session->fn_batch_init_tmac(1, 0, 1);
    } else {
        single_v013 = session->fn_batch_init_v013(1, 0, 1);
    }

    const int32_t max_gen = std::min<int32_t>(std::max<int32_t>(1, n_predict), session->n_ctx - n_prompt - 1);
    for (int32_t step = 0; step < max_gen; ++step) {
        if (session->cancel_requested.load(std::memory_order_relaxed)) {
            break;
        }

        float* logits = session->fn_get_logits_ith(session->ctx, (step == 0) ? last_logits_idx : 0);
        if (logits == nullptr) {
            logits = session->fn_get_logits_ith(session->ctx, -1);
        }
        if (logits == nullptr) {
            break;
        }

        llama_token next_tok = sample_next_token(logits, n_vocab, temperature, top_p, repeat_penalty, recent_tokens, rng);
        if (session->fn_is_eog(session->vocab, next_tok)) {
            break;
        }

        generated_tokens++;
        if (recent_tokens.size() >= 64) {
            recent_tokens.erase(recent_tokens.begin());
        }
        recent_tokens.push_back(next_tok);

        char piece_buf[256];
        int32_t piece_len = session->fn_token_to_piece(session->vocab, next_tok, piece_buf, sizeof(piece_buf), 0, false);
        std::string raw_piece;
        if (piece_len > 0 && piece_len <= static_cast<int32_t>(sizeof(piece_buf))) {
            raw_piece.assign(piece_buf, piece_len);
        } else if (piece_len < 0 && -piece_len < 4096) {
            std::vector<char> dyn_buf(-piece_len);
            int32_t retry_len = session->fn_token_to_piece(session->vocab, next_tok, dyn_buf.data(), static_cast<int32_t>(dyn_buf.size()), 0, false);
            if (retry_len > 0) {
                raw_piece.assign(dyn_buf.data(), retry_len);
            }
        }

        utf8_pending.append(raw_piece);
        size_t valid_len = valid_utf8_prefix_len(utf8_pending);
        std::string emit_utf8 = utf8_pending.substr(0, valid_len);
        utf8_pending.erase(0, valid_len);
        accumulated_text.append(emit_utf8);

        bool hit_stop_word = false;
        for (const auto& sw : stop_words) {
            if (!sw.empty() && accumulated_text.size() >= sw.size()) {
                if (accumulated_text.find(sw) != std::string::npos) {
                    hit_stop_word = true;
                    break;
                }
            }
        }

        if (j_callback != nullptr && on_piece_mid != nullptr) {
            jsize arr_len = static_cast<jsize>(emit_utf8.size());
            jbyteArray byte_arr = env->NewByteArray(arr_len);
            if (env->ExceptionCheck()) {
                break;
            }
            if (byte_arr != nullptr) {
                if (arr_len > 0) {
                    env->SetByteArrayRegion(
                        byte_arr,
                        0,
                        arr_len,
                        reinterpret_cast<const jbyte*>(emit_utf8.data())
                    );
                }
                env->CallVoidMethod(j_callback, on_piece_mid, byte_arr);
                env->DeleteLocalRef(byte_arr);
                if (env->ExceptionCheck()) {
                    break;
                }
            }
        }

        if (hit_stop_word || session->cancel_requested.load(std::memory_order_relaxed)) {
            break;
        }

        if (step + 1 < max_gen) {
            if (session->variant == RuntimeVariant::TMAC) {
                single_tmac.n_tokens = 1;
                single_tmac.token[0] = next_tok;
                single_tmac.pos[0] = n_past;
                single_tmac.n_seq_id[0] = 1;
                single_tmac.seq_id[0][0] = 0;
                single_tmac.logits[0] = 1;
                int32_t rc = session->fn_decode_tmac(session->ctx, single_tmac);
                if (rc != 0) {
                    break;
                }
            } else {
                single_v013.n_tokens = 1;
                single_v013.token[0] = next_tok;
                single_v013.pos[0] = n_past;
                single_v013.n_seq_id[0] = 1;
                single_v013.seq_id[0][0] = 0;
                single_v013.logits[0] = 1;
                int32_t rc = session->fn_decode_v013(session->ctx, single_v013);
                if (rc != 0) {
                    break;
                }
            }
            n_past++;
        }
    }

    if (session->variant == RuntimeVariant::TMAC) {
        session->fn_batch_free_tmac(single_tmac);
    } else {
        session->fn_batch_free_v013(single_v013);
    }

    auto decode_end = std::chrono::steady_clock::now();
    int64_t decode_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(decode_end - decode_start).count();

    jlongArray metrics = env->NewLongArray(4);
    if (metrics != nullptr) {
        jlong vals[4] = {
            static_cast<jlong>(n_prompt),
            static_cast<jlong>(generated_tokens),
            static_cast<jlong>(prefill_ns),
            static_cast<jlong>(decode_ns)
        };
        env->SetLongArrayRegion(metrics, 0, 4, vals);
    }
    return metrics;
}
