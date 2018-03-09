/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.messaging;

import brave.Tracing;
import brave.spring.rabbit.SpringRabbitTracing;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that registers a tracing instrumentation of
 * messaging components.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnBean(Tracing.class)
@AutoConfigureAfter({ TraceAutoConfiguration.class })
@ConditionalOnProperty(value = "spring.sleuth.messaging.enabled", matchIfMissing = true)
@EnableConfigurationProperties(SleuthMessagingProperties.class)
public class TraceMessagingAutoConfiguration {

	@Configuration
	@ConditionalOnClass(RabbitTemplate.class)
	protected static class SleuthRabbitConfiguration {
		@Bean
		@ConditionalOnMissingBean
		SpringRabbitTracing springRabbitTracing(Tracing tracing,
				SleuthMessagingProperties properties) {
			return SpringRabbitTracing.newBuilder(tracing)
					.remoteServiceName(properties.getMessaging().getRemoteServiceName())
					.build();
		}

		@Bean
		// for tests
		@ConditionalOnMissingBean
		SleuthRabbitBeanPostProcessor sleuthRabbitBeanPostProcessor(BeanFactory beanFactory) {
			return new SleuthRabbitBeanPostProcessor(beanFactory);
		}
	}
}

class SleuthRabbitBeanPostProcessor implements BeanPostProcessor {

	private final BeanFactory beanFactory;
	private SpringRabbitTracing tracing;

	SleuthRabbitBeanPostProcessor(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof RabbitTemplate) {
			return rabbitTracing()
					.decorateRabbitTemplate((RabbitTemplate) bean);
		} else if (bean instanceof SimpleRabbitListenerContainerFactory) {
			return rabbitTracing()
					.decorateSimpleRabbitListenerContainerFactory((SimpleRabbitListenerContainerFactory) bean);
		}
		return bean;
	}

	SpringRabbitTracing rabbitTracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(SpringRabbitTracing.class);
		}
		return this.tracing;
	}
}