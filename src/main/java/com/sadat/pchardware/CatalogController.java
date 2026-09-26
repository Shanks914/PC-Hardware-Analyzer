package com.sadat.pchardware;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

public class CatalogController {
    @FXML private VBox productList;
    @FXML private Label catalogStatus;
    private Consumer<Part> onAddPart = part -> {};

    public void setOnAddPart(Consumer<Part> action) {
        onAddPart = action;
    }

    public void setStatus(String status) {
        catalogStatus.setText(status);
    }

    public void showParts(List<Part> parts) {
        productList.getChildren().clear();
        for (Part part : parts) productList.getChildren().add(createPartCard(part));
    }

    private HBox createPartCard(Part part) {
        Label category = new Label(part.category().toUpperCase());
        category.getStyleClass().add("part-category");
        Label name = new Label(part.name());
        name.getStyleClass().add("part-name");
        name.setWrapText(true);
        Label specs = new Label(part.specs());
        specs.getStyleClass().add("muted-label");
        specs.setWrapText(true);
        VBox details = new VBox(6, category, name, specs);

        Label price = new Label(String.format("৳%,.0f", part.price()));
        price.getStyleClass().add("part-price");
        Button add = new Button("Add");
        add.getStyleClass().add("button-primary");
        add.setOnAction(event -> onAddPart.accept(part));
        VBox priceAndButton = new VBox(8, price, add);
        priceAndButton.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox card = new HBox(14, details, spacer, priceAndButton);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("panel");
        return card;
    }
}
