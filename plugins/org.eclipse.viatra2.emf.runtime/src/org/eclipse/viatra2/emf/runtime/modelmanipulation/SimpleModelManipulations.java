package org.eclipse.viatra2.emf.runtime.modelmanipulation;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.incquery.runtime.api.IncQueryEngine;
import org.eclipse.incquery.runtime.exception.IncQueryException;

public class SimpleModelManipulations extends AbstractModelManipulations{

	IncQueryEngine engine;
	
	public SimpleModelManipulations(IncQueryEngine engine) {
		  this.engine = engine;
	}
	
	@Override
	protected EObject doCreate(Resource res, EClass clazz)
			throws ModelManipulationException {
		EObject obj = EcoreUtil.create(clazz);
		res.getContents().add(obj);
		return obj;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected EObject doCreate(EObject container, EReference reference,
			EClass clazz) {
		EObject obj = EcoreUtil.create(clazz);
		if (reference.isMany()) {
			((EList)container.eGet(reference)).add(obj);
		} else {
			container.eSet(reference, obj);
		}
		return obj;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected void doAdd(EObject container, EAttribute attribute, Object value)
			throws ModelManipulationException {
		if (attribute.isMany()) {
			((EList)container.eGet(attribute)).add(value);
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected void doAdd(EObject container, EReference reference,
			EObject element) throws ModelManipulationException {
		((EList)container.eGet(reference)).add(element);
	}
	
	@Override
	protected void doRemove(EObject object) throws ModelManipulationException {
		EcoreUtil.remove(object);
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected void doRemove(EObject container, EReference reference,
			EObject element) throws ModelManipulationException {
		((EList)container.eGet(reference)).remove(element);
		
	}

	@Override
	protected <Type extends EObject> void doMoveTo(Type what, EList<Type> where)
			throws ModelManipulationException {
		try {
			engine.getBaseIndex().cheapMoveTo(what, where);
		} catch (IncQueryException e) {
			throw new ModelManipulationException(e);
		}
	}

}