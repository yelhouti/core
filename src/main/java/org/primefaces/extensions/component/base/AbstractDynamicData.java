/*
 * Copyright 2011-2015 PrimeFaces Extensions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id$
 */

package org.primefaces.extensions.component.base;

import org.primefaces.extensions.event.EventDataWrapper;
import org.primefaces.extensions.model.common.KeyData;
import org.primefaces.extensions.util.SavedEditableValueState;

import javax.faces.FacesException;
import javax.faces.application.Application;
import javax.faces.application.FacesMessage;
import javax.faces.component.ContextCallback;
import javax.faces.component.EditableValueHolder;
import javax.faces.component.NamingContainer;
import javax.faces.component.UIComponent;
import javax.faces.component.UIComponentBase;
import javax.faces.component.UINamingContainer;
import javax.faces.component.UIViewRoot;
import javax.faces.component.UniqueIdVendor;
import javax.faces.component.visit.VisitCallback;
import javax.faces.component.visit.VisitContext;
import javax.faces.component.visit.VisitResult;
import javax.faces.context.FacesContext;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.FacesEvent;
import javax.faces.event.PhaseId;
import javax.faces.event.PostValidateEvent;
import javax.faces.event.PreValidateEvent;
import javax.faces.render.Renderer;
import java.util.Map;
import org.primefaces.util.ComponentTraversalUtils;

/**
 * Abstract base class for all components with dynamic behavior like UIData.
 *
 * @author Oleg Varaksin / last modified by $Author$
 * @version $Revision$
 * @since 0.5
 */
public abstract class AbstractDynamicData extends UIComponentBase implements NamingContainer, UniqueIdVendor {

    protected static final String OPTIMIZED_PACKAGE = "org.primefaces.extensions.component.";

    protected KeyData data;
    private String clientId = null;
    private StringBuilder idBuilder = new StringBuilder();

    /**
     * Properties that are tracked by state saving.
     *
     * @author Oleg Varaksin / last modified by $Author$
     * @version $Revision$
     */
    protected enum PropertyKeys {

        saved,
        lastId,
        var,
        varContainerId,
        value;

        private String toString;

        PropertyKeys(String toString) {
            this.toString = toString;
        }

        PropertyKeys() {
        }

        @Override
        public String toString() {
            return ((this.toString != null) ? this.toString : super.toString());
        }
    }

    public String getVar() {
        return (String) getStateHelper().get(PropertyKeys.var);
    }

    public void setVar(String var) {
        getStateHelper().put(PropertyKeys.var, var);
    }

    public String getVarContainerId() {
        return (String) getStateHelper().get(PropertyKeys.varContainerId);
    }

    public void setVarContainerId(String varContainerId) {
        getStateHelper().put(PropertyKeys.varContainerId, varContainerId);
    }

    public Object getValue() {
        return getStateHelper().eval(PropertyKeys.value, null);
    }

    public void setValue(Object value) {
        getStateHelper().put(PropertyKeys.value, value);
    }

    /**
     * Finds instance of {@link org.primefaces.extensions.model.common.KeyData} by corresponding key.
     *
     * @param key unique key
     * @return KeyData found data
     */
    protected abstract KeyData findData(String key);

    /**
     * Processes children components during processDecodes(), processValidators(), processUpdates().
     *
     * @param context faces context {@link FacesContext}
     * @param phaseId current JSF phase id
     */
    protected abstract void processChildren(FacesContext context, PhaseId phaseId);

    /**
     * Visits children components during visitTree().
     *
     * @param context  visit context {@link VisitContext}
     * @param callback visit callback {@link VisitCallback}
     * @return boolean true - indicates that the children's visit is complete (e.g. all components that need to be visited have
     * been visited), false - otherwise.
     */
    protected abstract boolean visitChildren(VisitContext context, VisitCallback callback);

    /**
     * Searches a child component with the given clientId during invokeOnComponent() and invokes the callback on it if found.
     *
     * @param context  faces context {@link FacesContext}
     * @param clientId client Id
     * @param callback {@link ContextCallback}
     * @return boolean true - child component was found, else - otherwise
     */
    protected abstract boolean invokeOnChildren(FacesContext context, String clientId, ContextCallback callback);

    public void setData(String key) {
        if (data != null) {
            saveDescendantState();
        }

        data = findData(key);
        exposeVar();

        if (data != null) {
            restoreDescendantState();
        }
    }

    public void setData(KeyData keyData) {
        if (data != null) {
            saveDescendantState();
        }

        data = keyData;
        exposeVar();

        if (data != null) {
            restoreDescendantState();
        }
    }

    public void resetData() {
        if (data != null) {
            saveDescendantState();
        }

        data = null;
        exposeVar();
    }

    public KeyData getData() {
        return data;
    }

    @Override
    public String getClientId(FacesContext context) {
        if (this.clientId != null) {
            return this.clientId;
        }

        String id = getId();
        if (id == null) {
            UniqueIdVendor parentUniqueIdVendor = ComponentTraversalUtils.closestUniqueIdVendor(this);

            if (parentUniqueIdVendor == null) {
                UIViewRoot viewRoot = context.getViewRoot();

                if (viewRoot != null) {
                    id = viewRoot.createUniqueId(context, null);
                } else {
                    throw new FacesException("Cannot create clientId for " + this.getClass().getCanonicalName());
                }
            } else {
                id = parentUniqueIdVendor.createUniqueId(context, null);
            }

            this.setId(id);
        }

        UIComponent namingContainer = ComponentTraversalUtils.closestNamingContainer(this);
        if (namingContainer != null) {
            String containerClientId = namingContainer.getContainerClientId(context);

            if (containerClientId != null) {
                this.clientId =
                        this.idBuilder.append(containerClientId).append(UINamingContainer.getSeparatorChar(context)).append(id)
                                .toString();
                this.idBuilder.setLength(0);
            } else {
                this.clientId = id;
            }
        } else {
            this.clientId = id;
        }

        Renderer renderer = getRenderer(context);
        if (renderer != null) {
            this.clientId = renderer.convertClientId(context, this.clientId);
        }

        return this.clientId;
    }

    @Override
    public void setId(String id) {
        super.setId(id);

        this.clientId = null;
    }

    @Override
    public String getContainerClientId(FacesContext context) {
        String clientId = this.getClientId(context);

        KeyData data = getData();
        String key = (data != null ? data.getKey() : null);

        if (key == null) {
            return clientId;
        } else {
            String containerClientId =
                    idBuilder.append(clientId).append(UINamingContainer.getSeparatorChar(context)).append(key).toString();
            idBuilder.setLength(0);

            return containerClientId;
        }
    }

    @Override
    public void processDecodes(FacesContext context) {
        if (!isRendered()) {
            return;
        }

        pushComponentToEL(context, this);

        @SuppressWarnings("unchecked")
        Map<String, SavedEditableValueState> saved =
                (Map<String, SavedEditableValueState>) getStateHelper().get(PropertyKeys.saved);

        FacesMessage.Severity sev = context.getMaximumSeverity();
        boolean hasErrors = (sev != null && (FacesMessage.SEVERITY_ERROR.compareTo(sev) >= 0));

        if (saved == null) {
            getStateHelper().remove(PropertyKeys.saved);
        } else if (!hasErrors) {
            for (SavedEditableValueState saveState : saved.values()) {
                saveState.reset();
            }
        }

        processFacets(context, PhaseId.APPLY_REQUEST_VALUES, this);
        processChildren(context, PhaseId.APPLY_REQUEST_VALUES);

        try {
            decode(context);
        } catch (RuntimeException e) {
            context.renderResponse();
            throw e;
        } finally {
            popComponentFromEL(context);
        }
    }

    @Override
    public void processValidators(FacesContext context) {
        if (!isRendered()) {
            return;
        }

        pushComponentToEL(context, this);

        Application app = context.getApplication();
        app.publishEvent(context, PreValidateEvent.class, this);

        processFacets(context, PhaseId.PROCESS_VALIDATIONS, this);
        processChildren(context, PhaseId.PROCESS_VALIDATIONS);

        app.publishEvent(context, PostValidateEvent.class, this);
        popComponentFromEL(context);
    }

    @Override
    public void processUpdates(FacesContext context) {
        if (!isRendered()) {
            return;
        }

        pushComponentToEL(context, this);
        processFacets(context, PhaseId.UPDATE_MODEL_VALUES, this);
        processChildren(context, PhaseId.UPDATE_MODEL_VALUES);
        popComponentFromEL(context);
    }

    @Override
    public void queueEvent(FacesEvent event) {
        super.queueEvent(new EventDataWrapper(this, event, getData()));
    }

    @Override
    public void broadcast(FacesEvent event) throws AbortProcessingException {
        if (!(event instanceof EventDataWrapper)) {
            super.broadcast(event);

            return;
        }

        FacesContext context = FacesContext.getCurrentInstance();
        KeyData oldData = getData();
        EventDataWrapper eventDataWrapper = (EventDataWrapper) event;
        FacesEvent originalEvent = eventDataWrapper.getFacesEvent();
        UIComponent originalSource = (UIComponent) originalEvent.getSource();
        setData(eventDataWrapper.getData());

        UIComponent compositeParent = null;
        try {
            if (!UIComponent.isCompositeComponent(originalSource)) {
                compositeParent = getCompositeComponentParent(originalSource);
            }

            if (compositeParent != null) {
                compositeParent.pushComponentToEL(context, null);
            }

            originalSource.pushComponentToEL(context, null);
            originalSource.broadcast(originalEvent);
        } finally {
            originalSource.popComponentFromEL(context);
            if (compositeParent != null) {
                compositeParent.popComponentFromEL(context);
            }
        }

        setData(oldData);
    }

    @Override
    public boolean visitTree(VisitContext context, VisitCallback callback) {
        if (!isVisitable(context)) {
            return false;
        }

        final FacesContext fc = context.getFacesContext();
        KeyData oldData = getData();
        resetData();

        pushComponentToEL(fc, null);

        try {
            VisitResult result = context.invokeVisitCallback(this, callback);

            if (result == VisitResult.COMPLETE) {
                return true;
            }

            if (result == VisitResult.ACCEPT && !context.getSubtreeIdsToVisit(this).isEmpty()) {
                if (getFacetCount() > 0) {
                    for (UIComponent facet : getFacets().values()) {
                        if (facet.visitTree(context, callback)) {
                            return true;
                        }
                    }
                }

                if (visitChildren(context, callback)) {
                    return true;
                }
            }
        } finally {
            popComponentFromEL(fc);
            setData(oldData);
        }

        return false;
    }

    @Override
    public boolean invokeOnComponent(FacesContext context, String clientId, ContextCallback callback) {
        KeyData oldData = getData();
        resetData();

        try {
            if (clientId.equals(super.getClientId(context))) {
                this.pushComponentToEL(context, getCompositeComponentParent(this));
                callback.invokeContextCallback(context, this);

                return true;
            }

            if (getFacetCount() > 0) {
                for (UIComponent c : getFacets().values()) {
                    if (clientId.equals(c.getClientId(context))) {
                        callback.invokeContextCallback(context, c);

                        return true;
                    }
                }
            }

            return invokeOnChildren(context, clientId, callback);
        } catch (FacesException fe) {
            throw fe;
        } catch (Exception e) {
            throw new FacesException(e);
        } finally {
            popComponentFromEL(context);
            setData(oldData);
        }
    }

    protected void processFacets(FacesContext context, PhaseId phaseId, UIComponent component) {
        resetData();

        if (component.getFacetCount() > 0) {
            for (UIComponent facet : component.getFacets().values()) {
                if (phaseId == PhaseId.APPLY_REQUEST_VALUES) {
                    facet.processDecodes(context);
                } else if (phaseId == PhaseId.PROCESS_VALIDATIONS) {
                    facet.processValidators(context);
                } else if (phaseId == PhaseId.UPDATE_MODEL_VALUES) {
                    facet.processUpdates(context);
                } else {
                    throw new IllegalArgumentException();
                }
            }
        }
    }

    public String createUniqueId(FacesContext context, String seed) {
        Integer i = (Integer) getStateHelper().get(PropertyKeys.lastId);
        int lastId = ((i != null) ? i : 0);
        getStateHelper().put(PropertyKeys.lastId, ++lastId);

        return UIViewRoot.UNIQUE_ID_PREFIX + (seed == null ? lastId : seed);
    }

    protected void exposeVar() {
        FacesContext fc = FacesContext.getCurrentInstance();
        Map<String, Object> requestMap = fc.getExternalContext().getRequestMap();

        String var = getVar();
        if (var != null) {
            KeyData keyData = getData();
            if (keyData == null) {
                requestMap.remove(var);
            } else {
                requestMap.put(var, keyData.getData());
            }
        }

        String varContainerId = getVarContainerId();
        if (varContainerId != null) {
            String containerClientId = getContainerClientId(fc);
            if (containerClientId == null) {
                requestMap.remove(varContainerId);
            } else {
                requestMap.put(varContainerId, containerClientId);
            }
        }
    }

    protected void saveDescendantState() {
        for (UIComponent child : getChildren()) {
            saveDescendantState(FacesContext.getCurrentInstance(), child);
        }
    }

    protected void saveDescendantState(FacesContext context, UIComponent component) {
        // force id reset
        component.setId(component.getId());

        @SuppressWarnings("unchecked")
        Map<String, SavedEditableValueState> saved =
                (Map<String, SavedEditableValueState>) getStateHelper().get(PropertyKeys.saved);

        if (component instanceof EditableValueHolder) {
            EditableValueHolder input = (EditableValueHolder) component;
            SavedEditableValueState state = null;
            String clientId = component.getClientId(context);

            if (saved == null) {
                state = new SavedEditableValueState();
                getStateHelper().put(PropertyKeys.saved, clientId, state);
            }

            if (state == null) {
                state = saved.get(clientId);

                if (state == null) {
                    state = new SavedEditableValueState();
                    getStateHelper().put(PropertyKeys.saved, clientId, state);
                }
            }

            state.setValue(input.getLocalValue());
            state.setValid(input.isValid());
            state.setSubmittedValue(input.getSubmittedValue());
            state.setLocalValueSet(input.isLocalValueSet());
            state.setLabelValue(((UIComponent) input).getAttributes().get("label"));
        }

        for (UIComponent child : component.getChildren()) {
            saveDescendantState(context, child);
        }

        if (component.getFacetCount() > 0) {
            for (UIComponent facet : component.getFacets().values()) {
                saveDescendantState(context, facet);
            }
        }
    }

    protected void restoreDescendantState() {
        for (UIComponent child : getChildren()) {
            restoreDescendantState(FacesContext.getCurrentInstance(), child);
        }
    }

    protected void restoreDescendantState(FacesContext context, UIComponent component) {
        // force id reset
        component.setId(component.getId());

        @SuppressWarnings("unchecked")
        Map<String, SavedEditableValueState> saved =
                (Map<String, SavedEditableValueState>) getStateHelper().get(PropertyKeys.saved);

        if (saved == null) {
            return;
        }

        if (component instanceof EditableValueHolder) {
            EditableValueHolder input = (EditableValueHolder) component;
            String clientId = component.getClientId(context);

            SavedEditableValueState state = saved.get(clientId);
            if (state == null) {
                state = new SavedEditableValueState();
            }

            input.setValue(state.getValue());
            input.setValid(state.isValid());
            input.setSubmittedValue(state.getSubmittedValue());
            input.setLocalValueSet(state.isLocalValueSet());
            if (state.getLabelValue() != null) {
                ((UIComponent) input).getAttributes().put("label", state.getLabelValue());
            }
        }

        for (UIComponent child : component.getChildren()) {
            restoreDescendantState(context, child);
        }

        if (component.getFacetCount() > 0) {
            for (UIComponent facet : component.getFacets().values()) {
                restoreDescendantState(context, facet);
            }
        }
    }
}
