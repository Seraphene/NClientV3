package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.lang.reflect.Proxy;
import okhttp3.*;

final class TestChain {
    interface Transport { Response proceed(Request request) throws IOException; }
    static Interceptor.Chain create(Request request, Call call, Transport transport) {
        return (Interceptor.Chain) Proxy.newProxyInstance(Interceptor.Chain.class.getClassLoader(),
            new Class<?>[]{Interceptor.Chain.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "request": return request;
                    case "call": return call;
                    case "withConnectTimeout": return proxy;
                    case "proceed": return transport.proceed((Request) args[0]);
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
    }
    static Response response(Request request, int code, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }
}
