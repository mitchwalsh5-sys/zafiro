package com.niki914.libterm.backend.shizuku;

import com.niki914.libterm.backend.shizuku.ILibTermShizukuShellCallback;

interface ILibTermShizukuShellService {
    void destroy() = 16777114;

    long openSession(String cwd, ILibTermShizukuShellCallback callback) = 1;
    void write(long sessionId, in byte[] data) = 2;
    void close(long sessionId) = 3;
}
