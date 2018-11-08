/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import edu.umd.cs.mtc.MultithreadedTestCase;
import edu.umd.cs.mtc.TestFramework;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * The {@link AbstractConcurrentSessionOperationsIntegrationTests} class is an abstract base class encapsulating
 * functionality common to all concurrent {@link Session} operation and access based integration tests.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see edu.umd.cs.mtc.MultithreadedTestCase
 * @see edu.umd.cs.mtc.TestFramework
 * @since 2.1.0
 */
public abstract class AbstractConcurrentSessionOperationsIntegrationTests extends AbstractGemFireIntegrationTests {

	@Test
	public void concurrentSessionOperationsAreCorrect() throws Throwable {
		TestFramework.runOnce(new ConcurrentSessionOperationsTestCase(this));
	}

	protected static class AbstractConcurrentSessionOperationsTestCase extends MultithreadedTestCase {

		private final AbstractConcurrentSessionOperationsIntegrationTests testInstance;

		protected AbstractConcurrentSessionOperationsTestCase(
				AbstractConcurrentSessionOperationsIntegrationTests testInstance) {

			assertThat(testInstance).as("Test class instance must not be null").isNotNull();

			this.testInstance = testInstance;
		}

		protected AbstractConcurrentSessionOperationsIntegrationTests getTestInstance() {
			return this.testInstance;
		}

		@SuppressWarnings("unchecked")
		protected <T extends SessionRepository<? extends Session>> T getSessionRepository() {
			return (T) getTestInstance().getSessionRepository();
		}

		protected Session findById(String id) {
			return getTestInstance().get(id);
		}

		protected Session newSession() {
			return getTestInstance().createSession();
		}

		protected <T extends Session> T save(T session) {
			return getTestInstance().save(session);
		}
	}

	@SuppressWarnings("unused")
	public static class ConcurrentSessionOperationsTestCase extends AbstractConcurrentSessionOperationsTestCase {

		private final AtomicReference<String> sessionId = new AtomicReference<>(null);

		public ConcurrentSessionOperationsTestCase(AbstractConcurrentSessionOperationsIntegrationTests testInstance) {
			super(testInstance);
		}

		public void thread1() {

			Thread.currentThread().setName("User Session One");

			assertTick(0);

			Session session = newSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isNotEmpty();
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			session.setAttribute("attributeOne", "one");
			session.setAttribute("attributeTwo", "two");

			save(session);

			this.sessionId.set(session.getId());

			waitForTick(2);
			assertTick(2);

			// Save Session with no changes, no delta
			assertThat(session instanceof GemFireSession && ((GemFireSession) session).isDirty()).isFalse();

			save(session);
		}

		public void thread2() {

			Thread.currentThread().setName("User Session Two");

			waitForTick(1);
			assertTick(1);

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("two");

			session.setAttribute("attributeThree", "three");

			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo", "attributeThree");
			assertThat(session.<String>getAttribute("attributeThree")).isEqualTo("three");

			save(session);
		}

		@Override
		public void finish() {

			super.finish();

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo", "attributeThree");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("two");
			assertThat(session.<String>getAttribute("attributeThree")).isEqualTo("three");

			verify(this.<GemFireOperationsSessionRepository>getSessionRepository(), times(2))
				.doSave(eq(session));
		}
	}
}