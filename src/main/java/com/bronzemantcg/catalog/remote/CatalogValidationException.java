package com.bronzemantcg.catalog.remote;

/** A remote catalogue response was malformed or unsafe to activate as one complete snapshot. */
public final class CatalogValidationException extends Exception
{
	private static final long serialVersionUID = 1L;

	public CatalogValidationException(String message)
	{
		super(message);
	}

	public CatalogValidationException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
