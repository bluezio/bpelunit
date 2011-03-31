/**
 * This file belongs to the BPELUnit utility and Eclipse plugin set. See enclosed
 * license file for more information.
 */
package net.bpelunit.framework.control.util;

/**
 * Simple generic class which contains two objects.
 *
 * @author Antonio García-Domínguez
 * @version 1.0
 */
public class Pair<T, U> {

	private T fLeft;
	private U fRight;

	public Pair(T t, U u) {
		this.fLeft = t;
		this.fRight = u;
	}

	public T getLeft() {
		return fLeft;
	}

	public U getRight() {
		return fRight;
	}
}
