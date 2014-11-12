package jbse.algo;

import static jbse.algo.Util.NEGATIVE_ARRAY_SIZE_EXCEPTION;

import jbse.Type;
import jbse.Util;
import jbse.dec.DecisionProcedureAlgorithms.Outcome;
import jbse.exc.common.UnexpectedInternalException;
import jbse.exc.dec.DecisionException;
import jbse.exc.mem.FastArrayAccessNotAllowedException;
import jbse.exc.mem.InvalidOperandException;
import jbse.exc.mem.InvalidProgramCounterException;
import jbse.exc.mem.InvalidTypeException;
import jbse.exc.mem.ThreadStackEmptyException;
import jbse.mem.Array;
import jbse.mem.Expression;
import jbse.mem.Primitive;
import jbse.mem.Reference;
import jbse.mem.ReferenceArrayImmaterial;
import jbse.mem.ReferenceConcrete;
import jbse.mem.Simplex;
import jbse.mem.State;
import jbse.mem.Value;
import jbse.tree.DecisionAlternativeNewarray;

/**
 * Class for completing the semantics of the *newarray bytecodes
 * (newarray, anewarray, multianewarray). It decides over the
 * dimensions counts, a sheer numeric decision.
 * 
 * @author Pietro Braione
 */
abstract class MultipleStateGeneratorNewarray extends MultipleStateGenerator<DecisionAlternativeNewarray> {
	public MultipleStateGeneratorNewarray() {
		super(DecisionAlternativeNewarray.class);
	}
	
	//must be set by subclasses
	protected int pcOffset;
	protected Primitive[] dimensionsCounts;
	protected String arrayType;
	
	@Override
	protected void generateStates() 
	throws DecisionException, ThreadStackEmptyException {
		//calculates the number of layers that can be created right now
		//as the number of consecutive concrete dimensionsCounts members, 
		//plus one.
		final int layersToCreateNow;
		{
			int cnt = 0;
			boolean increment = true;
			for (Primitive l : this.dimensionsCounts) {
				if (increment) {
					++cnt;
				}
				increment = (l instanceof Simplex);
			}
			layersToCreateNow = cnt;
		}

		//builds the Primitive stating that all the dimension count values are nonnegative
		final Primitive countsNonNegative, countsNegative;
		try {
			Primitive tmp = this.ctx.calc.valBoolean(true);
			for (Primitive l : this.dimensionsCounts) {
				tmp = tmp.and(l.ge(this.ctx.calc.valInt(0)));
			}
			countsNonNegative = tmp;
			countsNegative = countsNonNegative.not();
		} catch (InvalidTypeException | InvalidOperandException e) {
			//TODO is it ok, or should we throw UnexpectedInternalException?
			this.state.createThrowableAndThrowIt(Util.VERIFY_ERROR);
			return;
		}

		this.ds = (result) -> {
			//invokes the decision procedure
			final Outcome o = ctx.decisionProcedure.decideNewarray(countsNonNegative, result);
			return o;
		};
		
		this.srs = (State s, DecisionAlternativeNewarray r) -> {
			final Primitive condTrue = (r.ok() ? countsNonNegative : countsNegative);
			s.assume(ctx.decisionProcedure.simplify(condTrue));
		};
		
		this.sus = (State s, DecisionAlternativeNewarray r) -> {
			if (r.ok()) {
				//calculates the number of dimensions as declared in arrayType
				//and checks that the length parameter does not exceed this number
				int dimDecl = Type.getDeclaredNumberOfDimensions(MultipleStateGeneratorNewarray.this.arrayType);
				if (dimDecl < MultipleStateGeneratorNewarray.this.dimensionsCounts.length) {
					s.createThrowableAndThrowIt(Util.VERIFY_ERROR);
					return;
				}

				//TODO check that arrayMemberSignature refers to an existing class (when should this be done???)
				//determines the signature of the array member
				//String arrayMemberSignature = arrayType.substring(dimDecl);
				//etc...

				//determines the initialization value
				final Value initValue;
				if (layersToCreateNow < dimensionsCounts.length) {
					//if it cannot create all layers now, initValue is a ReferenceArrayImmaterial,
					//which will allow to lazily build layers upon access to them
					final Primitive[] lConstr = new Primitive[dimensionsCounts.length - layersToCreateNow];
					System.arraycopy(dimensionsCounts, layersToCreateNow, lConstr, 0, lConstr.length);
					initValue = new ReferenceArrayImmaterial(arrayType, lConstr);
				} else {
					initValue = null; //means default value
				}

				final ReferenceConcrete toPush;
				try {
					toPush = createArrayMultilayer(s, initValue, layersToCreateNow);
				} catch (InvalidTypeException e) {
					s.createThrowableAndThrowIt(Util.VERIFY_ERROR);
					return;
				}

				//pushes the reference
				s.push(toPush);

				//increments the program counter
				try {
					s.incPC(MultipleStateGeneratorNewarray.this.pcOffset);
				} catch (InvalidProgramCounterException e) {
					s.createThrowableAndThrowIt(Util.VERIFY_ERROR);
				}
			} else {
				s.createThrowableAndThrowIt(NEGATIVE_ARRAY_SIZE_EXCEPTION);
			}
		};
		
		try {
			super.generateStates();
		} catch (DecisionException | RuntimeException e) {
			throw e;
		} catch (Exception e) {
			//this should never happen
			throw new UnexpectedInternalException(e);
		}
	}
	
	private ReferenceConcrete createArrayMultilayer(State s, Value initValue, int layersToCreateNow) 
	throws DecisionException, InvalidTypeException {
		//the reference to be pushed on the operand stack at the end of the
		//creation; note that it is initialized to null, but this is just 
		//to make the compiler happy. It will be initialized during the loop, 
		//at the end of which it must be nonnull 
		ReferenceConcrete retVal = null;
		
		//array of references to the Arrays created at the previous iteration
		Reference[] prev = null;

		//the number of arrays to be created in the current layer
		int toCreateInCurrentLayer = 1;

		//creates all the layers of monodimensional arrays that it can create now, 
		//initializes them, puts them in the heap, and sets toPush with a reference
		//to the topmost array
		for (int currentLayer = 0; currentLayer < layersToCreateNow; currentLayer++) {
			//caches the length of the arrays in the current layer 
			final Primitive currentLayerLength = MultipleStateGeneratorNewarray.this.dimensionsCounts[currentLayer];

			//determines if such length is (concretely or provably) 0 to early stop the 
			//creation process
			final int currentLayerLengthInt; //to use only when currentLayerLength instanceof Simplex
			boolean zeroBreak;
			if (currentLayerLength instanceof Simplex) {
				currentLayerLengthInt = ((Integer) ((Simplex) currentLayerLength).getActualValue());
				zeroBreak = (currentLayerLengthInt == 0); 
			} else {
				currentLayerLengthInt = -1; //not meaningful, set to an arbitrary value
				final Expression currentLayerLengthZero, currentLayerLengthNonzero;
				try {
					currentLayerLengthZero = (Expression) currentLayerLength.eq(this.ctx.calc.valInt(0));
					currentLayerLengthNonzero = (Expression) currentLayerLengthZero.not(); 
				} catch (InvalidOperandException | InvalidTypeException e) {
					//this should never happen
					throw new UnexpectedInternalException(e);
				} 
				zeroBreak = this.ctx.decisionProcedure.isSat(currentLayerLengthZero); 
				zeroBreak = zeroBreak && !this.ctx.decisionProcedure.isSat(currentLayerLengthNonzero);
			}

			//creates the i-th layer of arrays
			final Reference[] next = new Reference[toCreateInCurrentLayer];
			for (int k = 0; k < toCreateInCurrentLayer; ++k) {
				//creates the k-th array in the layer
				final String subarrayType = MultipleStateGeneratorNewarray.this.arrayType.substring(currentLayer);
				final ReferenceConcrete ref = s.createArray(initValue, currentLayerLength, subarrayType);

				//stores the reference to the created array
				if (currentLayer == 0) { //topmost reference
					retVal = ref;
				} else {				
					//stores the reference in one of the arrays created 
					//at (i-1)-th iteration; since only the objects in the
					//last layer are not normalized, we may use the fast
					//version of setting
					final int prevArraySize = toCreateInCurrentLayer / prev.length; //size of arrays created at iteration i-1
					final Simplex index = this.ctx.calc.valInt(k % prevArraySize);
					try {
						((Array) s.getObject(prev[k /prevArraySize])).setFast(index, ref);
					} catch (FastArrayAccessNotAllowedException | InvalidOperandException e) {
						//this should never happen
						throw new UnexpectedInternalException(e);
					}
				}

				//saves the created reference for initialization  
				//at (i+1)-th iteration
				next[k] = ref;
			}
			prev = next;
			if (currentLayerLength instanceof Simplex) {
				toCreateInCurrentLayer *= currentLayerLengthInt;
			}

			//exits if the length of the arrays in the current layer is zero
			if (zeroBreak) {
				break;
			}
		}
		
		return retVal;
	}
}
