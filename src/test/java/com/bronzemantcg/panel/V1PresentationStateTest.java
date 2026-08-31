package com.bronzemantcg.panel;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class V1PresentationStateTest
{
	@Test
	public void liveCapabilityActivatesAndPersistsPresentation()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		V1PresentationState state = new V1PresentationState(persistence);

		assertFalse(state.isActive(false));
		assertTrue(state.observeLiveCapability(true));
		assertTrue(state.isActive(false));
		assertFalse(state.observeLiveCapability(true));
	}

	@Test
	public void namesOnlyOrMalformedInputCannotActivatePresentation()
	{
		MemoryPersistence persistence = new MemoryPersistence();
		V1PresentationState state = new V1PresentationState(persistence);

		assertFalse(state.observeLiveCapability(false));
		assertFalse(state.isActive(false));
		assertFalse(persistence.written);
	}

	@Test
	public void profilePersistenceIsReadOnEveryUse()
	{
		SwitchingPersistence persistence = new SwitchingPersistence();
		V1PresentationState state = new V1PresentationState(persistence);

		state.observeLiveCapability(true);
		assertTrue(state.isActive(false));

		persistence.firstProfile = false;
		assertFalse(state.isActive(false));

		persistence.firstProfile = true;
		assertTrue(state.isActive(false));
	}

	private static final class SwitchingPersistence implements V1PresentationState.Persistence
	{
		private boolean firstProfile = true;
		private String firstValue;
		private String secondValue;

		@Override
		public String read()
		{
			return firstProfile ? firstValue : secondValue;
		}

		@Override
		public void write(String value)
		{
			if (firstProfile)
			{
				firstValue = value;
			}
			else
			{
				secondValue = value;
			}
		}
	}

	@Test
	public void liveCapabilityStillWorksWhenPersistenceFails()
	{
		V1PresentationState state = new V1PresentationState(new FailingPersistence());

		assertTrue(state.isActive(true));
		assertFalse(state.isActive(false));
	}

	private static final class MemoryPersistence implements V1PresentationState.Persistence
	{
		private String value;
		private boolean written;

		@Override
		public String read()
		{
			return value;
		}

		@Override
		public void write(String value)
		{
			this.value = value;
			written = true;
		}
	}

	private static final class FailingPersistence implements V1PresentationState.Persistence
	{
		@Override
		public String read()
		{
			throw new IllegalStateException("no profile");
		}

		@Override
		public void write(String value)
		{
			throw new IllegalStateException("no profile");
		}
	}
}
