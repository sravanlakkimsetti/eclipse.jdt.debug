/**********************************************************************
Copyright (c) 2000, 2002 IBM Corp. and others.
All rights reserved. This program and the accompanying materials
are made available under the terms of the Common Public License v0.5
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v05.html

Contributors:
    IBM Corporation - Initial implementation
**********************************************************************/

public class ThrowsNPE {
	
	public static void main(String[] args) {
		ThrowsNPE anObject = new ThrowsNPE();
		try {
			anObject.throwBaby();
		} catch(NullPointerException ne) {
			// do nothing
		}		
		anObject.throwBaby();
	}


	public void throwBaby() {
		throw new NullPointerException();
	}
}
