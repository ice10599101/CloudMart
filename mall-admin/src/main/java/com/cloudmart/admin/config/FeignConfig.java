package com.cloudmart.admin.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;

@Configuration
public class FeignConfig {

    @Bean
    FeignHttpMessageConverters feignHttpMessageConverters(
            ObjectProvider<ClientHttpMessageConvertersCustomizer> customizers,
            ObjectProvider<HttpMessageConverterCustomizer> cloudCustomizers) {
        FeignHttpMessageConverters fhmc = new FeignHttpMessageConverters(customizers, cloudCustomizers);
        List<HttpMessageConverter<?>> converters = fhmc.getConverters();
        if (!converters.isEmpty()) {
            return fhmc;
        }
        HttpMessageConverters hmc = HttpMessageConverters.forClient().registerDefaults().build();
        List<HttpMessageConverter<?>> fallback = new ArrayList<>();
        hmc.forEach(fallback::add);
        return new EagerFeignHttpMessageConverters(fallback);
    }

    private static final class EagerFeignHttpMessageConverters extends FeignHttpMessageConverters {

        private final List<HttpMessageConverter<?>> converters;

        EagerFeignHttpMessageConverters(List<HttpMessageConverter<?>> converters) {
            super(EmptyObjectProvider.instance(), EmptyObjectProvider.instance());
            this.converters = List.copyOf(converters);
        }

        @Override
        public List<HttpMessageConverter<?>> getConverters() {
            return converters;
        }
    }

    @SuppressWarnings("unchecked")
    private static final class EmptyObjectProvider<T> implements ObjectProvider<T> {

        private static final EmptyObjectProvider<?> INSTANCE = new EmptyObjectProvider<>();

        static <T> EmptyObjectProvider<T> instance() {
            return (EmptyObjectProvider<T>) INSTANCE;
        }

        @Override
        public T getObject() {
            return null;
        }

        @Override
        public T getIfAvailable() {
            return null;
        }

        @Override
        public T getIfUnique() {
            return null;
        }
    }
}
