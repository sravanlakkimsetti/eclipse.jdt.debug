package org.eclipse.jdt.internal.debug.eval.ast.engine;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
/**
 * Pushes a double literal onto the stack.
 */
public class PushDouble extends SimpleInstruction {
	
	private double fValue;
	
	public PushDouble(double value) {
		fValue = value;
	}
	
	public void execute() {
		pushNewValue(fValue);
	}
	
	public String toString() {
		return "push " + fValue;
	}

}

