// Tries KoLib's engine on this computer: try <model.gguf> <context> <max tokens> <system> <user> [<user 2>]
// With KL_AHEAD=1 in the environment, each question is read ahead of first (as the app does while it's typed), and
// how much the answer then had to read is shown.
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

#include "kl_llm.h"

int main(int argc, char ** argv) {
    if (argc < 6) {
        fprintf(stderr, "usage: try model.gguf n_ctx max_tokens system user [user2]\n");
        return 2;
    }
    kl::init("");
    std::string error;
    auto t0 = std::chrono::steady_clock::now();
    fprintf(stderr, "[weights a faster order would copy: %.2f GB]\n", kl::copied_size(argv[1]) / 1e9);
    // KL_NO_COPY=1: the weights used where they lie in the file (as the app does for a model too big to hold twice).
    kl::Engine * e = kl::load(argv[1], atoi(argv[2]), 2, getenv("KL_NO_COPY") == nullptr, error);
    if (!e) {
        fprintf(stderr, "couldn't load: %s\n", error.c_str());
        return 1;
    }
    auto secs = [&]() { return std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count(); };
    fprintf(stderr, "[loaded in %.1fs: %s]\n", secs(), kl::describe(e).c_str());
    std::vector<kl::Message> messages;
    if (argv[4][0]) messages.push_back({"system", argv[4]});
    for (int turn = 5; turn < argc; turn++) {
        if (getenv("KL_AHEAD")) {
            auto ahead = messages;
            ahead.push_back({"user", kl::ASK_MARK});
            t0 = std::chrono::steady_clock::now();
            kl::Result a = kl::prefill(e, ahead);
            fprintf(stderr, "[read ahead: result %d, %d tokens in %.1fs]\n", (int) a, e->last_read, secs());
        }
        messages.push_back({"user", argv[turn]});
        std::string said;
        t0 = std::chrono::steady_clock::now();
        double read = 0;
        int pieces = 0;
        kl::Result r = kl::generate(e, messages, atoi(argv[3]), 0.3f,
            [&](float p) { if (p >= 1.0f) read = secs(); return true; },
            [&](const std::string & s) { said += s; pieces++; fputs(s.c_str(), stdout); fflush(stdout); return true; });
        fprintf(stderr, "\n[result %d, %d prompt tokens read in %.1fs, %d pieces in %.1fs%s%s]\n", (int) r, e->last_read, read, pieces, secs() - read,
                e->error.empty() ? "" : ", error: ", e->error.c_str());
        messages.push_back({"assistant", said});
    }
    kl::unload(e);
    return 0;
}
