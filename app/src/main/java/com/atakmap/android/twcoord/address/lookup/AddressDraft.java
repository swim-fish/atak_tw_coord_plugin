package com.atakmap.android.twcoord.address.lookup;

import java.util.Objects;

/** Immutable canonical address draft shared by full and structured input modes. */
public final class AddressDraft {
  private final String rawAddress;
  private final String normalizedAddress;
  private final AddressComponents components;
  private final String unclassifiedText;
  private final AddressInputMode mode;
  private final long draftRevision;
  private final AddressValidation validation;

  public AddressDraft(
      String rawAddress,
      String normalizedAddress,
      AddressComponents components,
      String unclassifiedText,
      AddressInputMode mode,
      long draftRevision,
      AddressValidation validation) {
    this.rawAddress = valueOrEmpty(rawAddress);
    this.normalizedAddress = valueOrEmpty(normalizedAddress);
    this.components = Objects.requireNonNull(components, "components");
    this.unclassifiedText = valueOrEmpty(unclassifiedText);
    this.mode = Objects.requireNonNull(mode, "mode");
    this.draftRevision = draftRevision;
    this.validation = Objects.requireNonNull(validation, "validation");
  }

  public static AddressDraft empty(long revision, AddressInputMode mode) {
    return new AddressDraft(
        "", "", new AddressComponents("", "", "", ""), "", mode, revision, AddressValidation.EMPTY);
  }

  public String rawAddress() {
    return rawAddress;
  }

  public String normalizedAddress() {
    return normalizedAddress;
  }

  public AddressComponents components() {
    return components;
  }

  public String unclassifiedText() {
    return unclassifiedText;
  }

  public AddressInputMode mode() {
    return mode;
  }

  public long draftRevision() {
    return draftRevision;
  }

  public AddressValidation validation() {
    return validation;
  }

  public String composeStructured() {
    return components.compose() + unclassifiedText;
  }

  /** Text shown in the fourth structured field, including text not safely classified. */
  public String structuredTail() {
    return components.tail() + unclassifiedText;
  }

  public AddressDraft withMode(AddressInputMode nextMode) {
    return new AddressDraft(
        rawAddress,
        normalizedAddress,
        components,
        unclassifiedText,
        nextMode,
        draftRevision,
        validation);
  }

  public AddressDraft withValidation(AddressValidation nextValidation) {
    return new AddressDraft(
        rawAddress,
        normalizedAddress,
        components,
        unclassifiedText,
        mode,
        draftRevision,
        nextValidation);
  }

  private static String valueOrEmpty(String value) {
    return value != null ? value : "";
  }
}
