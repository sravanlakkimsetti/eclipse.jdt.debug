package org.eclipse.jdt.internal.debug.eval.ast.engine;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.debug.eval.ast.model.IObject;
import org.eclipse.jdt.debug.eval.ast.model.IRuntimeContext;
 
/**
 * Pushes the 'this' object onto the stack.
 */
public class PushThis extends SimpleInstruction {
	
	public void execute() throws CoreException {
		IRuntimeContext context= getContext();
		IObject rec = context.getThis();
		if (rec == null) {
			// static context
			push(context.getReceivingType());
		} else {
			push(rec);
		}
	}

	public String toString() {
		return "push 'this'";
	}
}

