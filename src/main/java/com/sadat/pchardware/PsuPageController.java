package com.sadat.pchardware;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Local PSU chooser with catalog-backed filters and GPU wattage checks. */
public class PsuPageController {
    private static final int PAGE_SIZE = 10;
    @FXML private BorderPane root;
    @FXML private TextField minPriceField, maxPriceField, searchField;
    @FXML private ComboBox<String> availabilityFilter, brandFilter, wattageFilter, modularityFilter;
    @FXML private ComboBox<String> efficiencyFilter, formFactorFilter, atxVersionFilter, sortBox;
    @FXML private CheckBox compatibleOnlyFilter;
    @FXML private VBox productRows;
    @FXML private Label resultCount, compatibilityHint, pageStatus, paginationLabel;
    @FXML private Button previousButton, nextButton;
    private List<Part> psus = List.of(), filtered = List.of();
    private Part selectedPsu, selectedGpu;
    private Consumer<Part> onAdd = part -> {};
    private Runnable onBack = () -> {};
    private int page;

    @FXML private void initialize() {
        options(availabilityFilter, "Any availability", "In stock", "Pre-order", "Out of stock", "Not specified");
        options(wattageFilter, "Any wattage", "Up to 499W", "500–799W", "800–999W", "1000W and above");
        options(modularityFilter, "Any modularity", "Full Modular", "Semi Modular", "Non Modular");
        options(efficiencyFilter, "Any efficiency", "80+ Titanium", "80+ Platinum", "80+ Gold", "80+ Silver", "80+ Bronze", "80+ White", "80+");
        options(formFactorFilter, "Any form factor", "ATX", "SFX");
        options(atxVersionFilter, "Any ATX version", "ATX 2.4", "ATX 3.0", "ATX 3.1");
        options(sortBox, "Recommended", "Price: low to high", "Price: high to low", "Wattage: low to high", "Wattage: high to low", "Name: A to Z");
        sortBox.setOnAction(e -> applyFilters());
        compatibleOnlyFilter.setDisable(true);
    }
    private void options(ComboBox<String> box,String... values) { box.setItems(FXCollections.observableArrayList(values)); box.getSelectionModel().selectFirst(); }
    public Node getView() { return root; }
    public void setActions(Runnable back, Consumer<Part> add) { onBack=back; onAdd=add; }
    public void setParts(List<Part> parts) {
        psus=parts.stream().filter(p -> p.category().equalsIgnoreCase("PSU")).toList();
        Set<String> brands=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        psus.forEach(p -> { if(!value(p,"brand").isBlank()) brands.add(value(p,"brand")); });
        brandFilter.getItems().setAll("Any brand"); brandFilter.getItems().addAll(brands); brandFilter.getSelectionModel().selectFirst();
        applyFilters(); if(psus.isEmpty()) pageStatus.setText("No power supplies found in catalog/psus.json. Reload local data from the build page.");
    }
    public void setSelectedGpu(Part gpu) {
        selectedGpu=gpu; compatibleOnlyFilter.setDisable(gpu==null);
        if(gpu==null) { compatibilityHint.setText("Choose a graphics card to enable recommended-wattage filtering."); compatibleOnlyFilter.setSelected(false); }
        else compatibilityHint.setText("Selected GPU: " + gpu.name() + " · Recommended PSU: " + value(gpu,"recommendedPsuW") + "W.");
        applyFilters();
    }
    public void setSelectedPsu(Part psu) { selectedPsu=psu; renderPage(); }
    public void setSearchQuery(String query) { searchField.setText(query==null?"":query); applyFilters(); }
    @FXML private void backToBuild() { onBack.run(); }
    @FXML private void clearFilters() {
        minPriceField.clear(); maxPriceField.clear(); searchField.clear();
        for(ComboBox<String> box:List.of(availabilityFilter,brandFilter,wattageFilter,modularityFilter,efficiencyFilter,formFactorFilter,atxVersionFilter,sortBox)) box.getSelectionModel().selectFirst();
        compatibleOnlyFilter.setSelected(false); applyFilters();
    }
    @FXML private void applyFilters() {
        page=0; Double min=parsePrice(minPriceField.getText()),max=parsePrice(maxPriceField.getText());
        if((!minPriceField.getText().isBlank()&&min==null)||(!maxPriceField.getText().isBlank()&&max==null)) { pageStatus.setText("Enter valid numeric values for the price range."); return; }
        if(min!=null&&max!=null&&min>max) { pageStatus.setText("Minimum price cannot be greater than maximum price."); return; }
        String query=searchField.getText().trim().toLowerCase(Locale.ROOT);
        filtered=psus.stream().filter(p->min==null||p.price()>=min).filter(p->max==null||p.price()<=max)
                .filter(p->any(availabilityFilter)||equals(p,"availability",selected(availabilityFilter)))
                .filter(p->any(brandFilter)||equals(p,"brand",selected(brandFilter)))
                .filter(p->wattageMatches(number(p,"wattage"),selected(wattageFilter)))
                .filter(p->any(modularityFilter)||equals(p,"modularity",selected(modularityFilter)))
                .filter(p->any(efficiencyFilter)||equals(p,"efficiency",selected(efficiencyFilter)))
                .filter(p->any(formFactorFilter)||equals(p,"formFactor",selected(formFactorFilter)))
                .filter(p->any(atxVersionFilter)||equals(p,"atxVersion",selected(atxVersionFilter)))
                .filter(p->!compatibleOnlyFilter.isSelected()||compatibilityIssue(p).isBlank())
                .filter(p->query.isBlank()||searchable(p).contains(query)).sorted(comparator(selected(sortBox))).toList();
        pageStatus.setText(psus.isEmpty()?"PSU catalog is empty.":"Local power supply catalog · Example BDT prices and availability for the course project."); renderPage();
    }
    @FXML private void previousPage() { if(page>0){page--;renderPage();} }
    @FXML private void nextPage() { if((page+1)*PAGE_SIZE<filtered.size()){page++;renderPage();} }
    private void renderPage() {
        productRows.getChildren().clear(); resultCount.setText(filtered.size()+(filtered.size()==1?" power supply":" power supplies"));
        int pages=Math.max(1,(filtered.size()+PAGE_SIZE-1)/PAGE_SIZE); page=Math.min(page,pages-1);
        paginationLabel.setText("Page "+(page+1)+" of "+pages); previousButton.setDisable(page==0); nextButton.setDisable(page+1>=pages);
        for(Part p:filtered.subList(page*PAGE_SIZE,Math.min((page+1)*PAGE_SIZE,filtered.size()))) productRows.getChildren().add(card(p));
    }
    private HBox card(Part psu) {
        VBox detail=new VBox(6); Label name=new Label(psu.name()); name.getStyleClass().add("processor-name"); name.setWrapText(true);
        Label specs=new Label(psu.specs()); specs.getStyleClass().add("processor-specs"); specs.setWrapText(true);
        Label compat=new Label(compatibilityText(psu)); compat.getStyleClass().add("compatibility-status");
        compat.getStyleClass().add(selectedGpu==null?"compatibility-neutral":compatibilityIssue(psu).isBlank()?"compatibility-ok":"compatibility-error");
        detail.getChildren().addAll(name,specs,compat); HBox.setHgrow(detail,Priority.ALWAYS);
        VBox buy=new VBox(10); buy.setMinWidth(145); buy.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        Label price=new Label(String.format("৳%,.0f",psu.price())); price.getStyleClass().add("processor-price");
        boolean chosen=selectedPsu!=null&&selectedPsu.name().equalsIgnoreCase(psu.name());
        Button add=new Button(chosen?"Selected · Replace":"Add to build"); add.getStyleClass().add(chosen?"button-primary":"button-secondary"); add.setOnAction(e->onAdd.accept(psu));
        buy.getChildren().addAll(price,add); HBox row=new HBox(18,detail,buy); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); row.getStyleClass().add("processor-card"); return row;
    }
    private String compatibilityText(Part psu) {
        if(selectedGpu==null) return "Select a graphics card to verify recommended wattage.";
        String issue=compatibilityIssue(psu); return issue.isBlank()?"Wattage meets the selected GPU recommendation.":"Not compatible: "+issue;
    }
    private String compatibilityIssue(Part psu) {
        if(selectedGpu==null)return "Graphics card is not selected.";
        int recommended=number(selectedGpu,"recommendedPsuW"), supplied=number(psu,"wattage");
        if(recommended<=0)return "GPU recommended wattage is missing from its catalog entry.";
        if(supplied<=0)return "PSU wattage is missing from its catalog entry.";
        return supplied<recommended?"GPU recommends at least "+recommended+"W, but this PSU provides "+supplied+"W.":"";
    }
    private boolean any(ComboBox<String> box){return selected(box).startsWith("Any ");}
    private String selected(ComboBox<String> box){return box.getValue()==null?"":box.getValue();}
    private boolean equals(Part p,String key,String expected){return p.attribute(key).equalsIgnoreCase(expected);}
    private int number(Part p,String key){try{return Integer.parseInt(value(p,key));}catch(Exception ignored){return 0;}}
    private String value(Part p,String key){return p.attribute(key);}
    private boolean wattageMatches(int w,String filter){return switch(filter){case "Up to 499W"->w<500;case "500–799W"->w>=500&&w<800;case "800–999W"->w>=800&&w<1000;case "1000W and above"->w>=1000;default->true;};}
    private String searchable(Part p){return(p.name()+" "+p.specs()+" "+String.join(" ",p.attributes().values())).toLowerCase(Locale.ROOT);}
    private Double parsePrice(String s){if(s==null||s.isBlank())return null;try{return Double.parseDouble(s.replace(",","").trim());}catch(NumberFormatException e){return null;}}
    private Comparator<Part> comparator(String sort){return switch(sort){case "Price: low to high"->Comparator.comparingDouble(Part::price);case "Price: high to low"->Comparator.comparingDouble(Part::price).reversed();case "Wattage: low to high"->Comparator.comparingInt((Part p)->number(p,"wattage"));case "Wattage: high to low"->Comparator.comparingInt((Part p)->number(p,"wattage")).reversed();case "Name: A to Z"->Comparator.comparing(Part::name,String.CASE_INSENSITIVE_ORDER);default->Comparator.comparingInt((Part p)->number(p,"wattage")).reversed();};}
}
