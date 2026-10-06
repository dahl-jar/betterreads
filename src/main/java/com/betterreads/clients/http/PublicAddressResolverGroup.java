package com.betterreads.clients.http;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.InetNameResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

final class PublicAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private final PublicUrlGuard guard;

    PublicAddressResolverGroup(final PublicUrlGuard guard) {
        super();
        this.guard = guard;
    }

    // PMD.DoNotUseThreads: Netty hands each resolver the event loop it runs on.
    @SuppressWarnings("PMD.DoNotUseThreads")
    @Override
    protected AddressResolver<InetSocketAddress> newResolver(final EventExecutor executor) {
        return new InetSocketAddressResolver(executor, new PublicNameResolver(executor, guard));
    }

    // PMD.DoNotUseThreads: Netty hands each resolver the event loop it runs on.
    @SuppressWarnings("PMD.DoNotUseThreads")
    private static final class PublicNameResolver extends InetNameResolver {

        private final PublicUrlGuard guard;

        PublicNameResolver(final EventExecutor executor, final PublicUrlGuard guard) {
            super(executor);
            this.guard = guard;
        }

        @Override
        protected void doResolve(final String host, final Promise<InetAddress> promise) {
            try {
                promise.setSuccess(publicAddresses(host).getFirst());
            } catch (UnknownHostException ex) {
                promise.setFailure(ex);
            }
        }

        @Override
        protected void doResolveAll(final String host, final Promise<List<InetAddress>> promise) {
            try {
                promise.setSuccess(publicAddresses(host));
            } catch (UnknownHostException ex) {
                promise.setFailure(ex);
            }
        }

        private List<InetAddress> publicAddresses(final String host) throws UnknownHostException {
            final InetAddress[] addresses = guard.addressesOf(host);
            if (!guard.arePublic(addresses)) {
                throw new UnknownHostException("refused non-public address for " + host);
            }
            return List.of(addresses);
        }
    }
}
