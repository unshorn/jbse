package jbse.dec;

import jbse.tree.DecisionAlternative_XLOAD_GETX_Aliases;
import jbse.tree.DecisionAlternative_XLOAD_GETX_Expands;
import jbse.tree.DecisionAlternative_XLOAD_GETX_Null;
import jbse.val.ReferenceSymbolic;

/**
 * A Concrete Factory for decision alternatives used when the reference
 * that must be resolved comes from a local variable.
 *  
 * @author Pietro Braione
 *
 */
class DecisionAlternativeReferenceFromLocalVariableFactory 
implements DecisionAlternativeReferenceFactory<DecisionAlternative_XLOAD_GETX_Aliases, 
DecisionAlternative_XLOAD_GETX_Expands, DecisionAlternative_XLOAD_GETX_Null> {
	@Override
	public DecisionAlternative_XLOAD_GETX_Aliases 
	createAlternativeRefAliases(ReferenceSymbolic refToResolve, long objectPosition, String objectOrigin, int branchNumber) {
		return new DecisionAlternative_XLOAD_GETX_Aliases(refToResolve, objectPosition, objectOrigin, branchNumber);
	}

	@Override
	public DecisionAlternative_XLOAD_GETX_Expands 
	createAlternativeRefExpands(ReferenceSymbolic refToResolve, String className, int branchNumber) {
		return new DecisionAlternative_XLOAD_GETX_Expands(refToResolve, className, branchNumber);
	}

	@Override
	public DecisionAlternative_XLOAD_GETX_Null 
	createAlternativeRefNull(ReferenceSymbolic refToResolve, int branchNumber) {
		return new DecisionAlternative_XLOAD_GETX_Null(refToResolve, branchNumber);
	}
}