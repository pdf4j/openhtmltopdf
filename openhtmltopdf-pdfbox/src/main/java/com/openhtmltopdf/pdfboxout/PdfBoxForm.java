package com.openhtmltopdf.pdfboxout;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.COSArrayList;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDFileSpecification;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionSubmitForm;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.parser.FSCMYKColor;
import com.openhtmltopdf.css.parser.FSColor;
import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.RenderingContext;


public class PdfBoxForm {
    private final Element element;
    private final List<ControlFontPair> controls = new ArrayList<PdfBoxForm.ControlFontPair>();
    private final List<String> controlNames = new ArrayList<String>();
    private final List<Control> submits = new ArrayList<PdfBoxForm.Control>(1);
    
    public static class Control {
        public final Box box;
        private final PDPage page;
        private final AffineTransform transform;
        private final RenderingContext c;
        private final float pageHeight;
        
        public Control(Box box, PDPage page, AffineTransform transform, RenderingContext c, float pageHeight) {
            this.box = box;
            this.page = page;
            this.transform = transform;
            this.c = c;
            this.pageHeight = pageHeight;
        }
    }
    
    private static class ControlFontPair {
        private final String fontName;
        private final Control control;
        
        private ControlFontPair(Control control, String fontName) {
            this.control = control;
            this.fontName = fontName;
        }
    }
    
    private PdfBoxForm(Element element) {
        this.element = element;
    }
    
    public static PdfBoxForm createForm(Element e) {
        return new PdfBoxForm(e);
    }

    public void addControl(Control ctrl, String fontName) {
        controls.add(new ControlFontPair(ctrl, fontName));
    }
    
    private static Integer getNumber(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public int process(PDAcroForm acro, int startId, Box root, PdfBoxOutputDevice od) throws IOException {
        int  i = startId;

        for (ControlFontPair pair : controls) {
            i++;
            
            Control ctrl = pair.control;
            
            if ((ctrl.box.getElement().getNodeName().equals("input") &&
                 ctrl.box.getElement().getAttribute("type").equals("text")) ||
                (ctrl.box.getElement().getNodeName().equals("textarea")) ||
                (ctrl.box.getElement().getNodeName().equals("input") && 
                 ctrl.box.getElement().getAttribute("type").equals("password"))) {

                // Start with the text controls (text, password and textarea).
                PDTextField field = new PDTextField(acro);
                
                FSColor color = ctrl.box.getStyle().getColor();
                String colorOperator = "";
                
                if (color instanceof FSRGBColor) {
                    FSRGBColor rgb = (FSRGBColor) color;
                    float r = (float) rgb.getRed() / 255;
                    float g = (float) rgb.getGreen() / 255;
                    float b = (float) rgb.getBlue() / 255;
                    
                    colorOperator =
                            String.format(Locale.US, "%.4f", r) + ' ' + 
                            String.format(Locale.US, "%.4f", g) + ' ' + 
                            String.format(Locale.US, "%.4f", b) + ' ' +
                            "rg";
                } else if (color instanceof FSCMYKColor) {
                    FSCMYKColor cmyk = (FSCMYKColor) color;
                    float c = cmyk.getCyan();
                    float m = cmyk.getMagenta();
                    float y = cmyk.getYellow();
                    float k = cmyk.getBlack();
                    
                    colorOperator = 
                            String.format(Locale.US, "%.4f", c) + ' ' +
                            String.format(Locale.US, "%.4f", m) + ' ' +
                            String.format(Locale.US, "%.4f", y) + ' ' +
                            String.format(Locale.US, "%.4f", k) + ' ' +
                            "k";
                }

                String fontInstruction = "/" + pair.fontName + " 12 Tf";
                field.setDefaultAppearance(fontInstruction + ' ' + colorOperator);
                
                field.setPartialName("OpenHTMLCtrl" + i); // Internal name.
                controlNames.add("OpenHTMLCtrl" + i);
                
                field.setDefaultValue(ctrl.box.getElement().getAttribute("value")); // The reset value.
                field.setValue(ctrl.box.getElement().getAttribute("value"));        // The original value.
            
                if (getNumber(ctrl.box.getElement().getAttribute("max-length")) != null) {
                    field.setMaxLen(getNumber(ctrl.box.getElement().getAttribute("max-length")));
                }
                
                if (ctrl.box.getElement().hasAttribute("required")) {
                    field.setRequired(true);
                }
                
                if (ctrl.box.getElement().hasAttribute("readonly")) {
                    field.setReadOnly(true);
                }
                
                if (ctrl.box.getElement().getNodeName().equals("textarea")) {
                    field.setMultiline(true);
                } else if (ctrl.box.getElement().getAttribute("type").equals("password")) {
                    field.setPassword(true);
                }
                
                field.setMappingName(ctrl.box.getElement().getAttribute("name")); // Export name.
                
                PDAnnotationWidget widget = field.getWidgets().get(0);

                Rectangle2D rect2D = PdfBoxLinkManager.createTargetArea(ctrl.c, ctrl.box, ctrl.pageHeight, ctrl.transform, root, od);
                PDRectangle rect = new PDRectangle((float) rect2D.getMinX(), (float) rect2D.getMinY(), (float) rect2D.getWidth(), (float) rect2D.getHeight());

                widget.setRectangle(rect);
                widget.setPage(ctrl.page);
              
                ctrl.page.getAnnotations().add(widget);
                acro.getFields().add(field);
            }
            else if ((ctrl.box.getElement().getNodeName().equals("input") && 
                      ctrl.box.getElement().getAttribute("type").equals("submit")) ||
                     (ctrl.box.getElement().getNodeName().equals("button") &&
                      !ctrl.box.getElement().getAttribute("type").equals("button"))) {
                // We've got a submit control for this form.
                submits.add(ctrl);
            }
        }
        
        // We do submit controls last as we need all the fields in this form.
        for (Control ctrl : submits) {
            PDPushButton btn = new PDPushButton(acro);
            btn.setPushButton(true);
            PDAnnotationWidget widget = btn.getWidgets().get(0);
            
            Rectangle2D rect2D = PdfBoxLinkManager.createTargetArea(ctrl.c, ctrl.box, ctrl.pageHeight, ctrl.transform, root, od);
            PDRectangle rect = new PDRectangle((float) rect2D.getMinX(), (float) rect2D.getMinY(), (float) rect2D.getWidth(), (float) rect2D.getHeight());

            widget.setRectangle(rect);
            widget.setPage(ctrl.page);
            
            PDFileSpecification fs = PDFileSpecification.createFS(new COSString(element.getAttribute("action")));

            COSArrayList<String> fieldsToInclude = new COSArrayList<String>();
            fieldsToInclude.addAll(controlNames);
            
            PDActionSubmitForm submit = new PDActionSubmitForm();
            submit.setFields(fieldsToInclude.toList());
            submit.setFile(fs);

            if (!element.getAttribute("method").equalsIgnoreCase("post")) {
                // Default method is get.
                submit.setFlags(1 << 4); // The get flag is bit 4.
            }

            widget.setAction(submit);

            ctrl.page.getAnnotations().add(widget);
            acro.getFields().add(btn);
        }
        
        return i;
    }
}