/*
 * Copyright (C) 2012 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.impl.adapter;

import java.util.Map;

import org.auraframework.Aura;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.TypeDef;
import org.auraframework.expression.PropertyReference;
import org.auraframework.impl.util.AuraUtil;
import org.auraframework.impl.util.BrowserInfo;
import org.auraframework.instance.GlobalValueProvider;
import org.auraframework.instance.ValueProviderType;
import org.auraframework.system.AuraContext;
import org.auraframework.throwable.quickfix.InvalidExpressionException;
import org.auraframework.throwable.quickfix.QuickFixException;

import com.google.common.collect.Maps;

/**
 * $Browser global value provider, backed by data from BrowserInfo
 *
 * @author eanderson
 */
public class BrowserValueProvider implements GlobalValueProvider {
    public static final String IS_TABLET  = "isTablet";
    public static final String IS_PHONE  = "isPhone";
    public static final String IS_ANDROID = "isAndroid";
    public static final String FORM_FACTOR = "formFactor";
    public static final String IS_IPHONE = "isIPhone";
    public static final String IS_IPAD = "isIPad";
    public static final String IS_IOS = "isIOS";

    
    private Map<String, Object> browserDetails;

    private void parse() {
        AuraContext context = Aura.getContextService().getCurrentContext();
        Map<String, Object> m = Maps.newHashMapWithExpectedSize(32);
        if (context == null) { 
            this.browserDetails = AuraUtil.immutableMap(m);
            return;
        }
        BrowserInfo b = new BrowserInfo(context == null ? "" : context.getClient().getUserAgent());
        m.put(IS_TABLET, b.isTablet());
        m.put(IS_PHONE, b.isPhone());
        m.put(IS_ANDROID, b.isAndroid());
        m.put(FORM_FACTOR, b.getFormFactor() );
        m.put(IS_IPHONE, b.isIPhone());
        m.put(IS_IPAD, b.isIPad());
        m.put(IS_IOS, b.isIOS());

        this.browserDetails = AuraUtil.immutableMap(m);
    }

    public BrowserValueProvider() {
        browserDetails=null;
    }

    @Override
    public Object getValue(PropertyReference key) throws QuickFixException {
        return getData().get(key.getRoot());
    }

    @Override
    public ValueProviderType getValueProviderKey() {
        return ValueProviderType.BROWSER;
    }

    @Override
    public DefDescriptor<TypeDef> getReturnTypeDef() {
        return null;
    }

    @Override
    public void validate(PropertyReference expr) throws InvalidExpressionException {
        if (expr.size() != 1 || !getData().containsKey(expr.getRoot())) {
            if (expr.getRoot().toString().equals("isContainer")) // TODO - remove InputDate and InputDateTime dependency on isContainer, which is SFDC specific
                return;
            throw new InvalidExpressionException("No property on $Browser for key: " + expr, expr.getLocation());
        }
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Map<String, ?> getData() {
        if (browserDetails == null) {
            parse();
        }
        
        return browserDetails;
    }

}
