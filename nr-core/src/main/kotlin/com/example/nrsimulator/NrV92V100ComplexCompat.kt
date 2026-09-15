package com.example.nrsimulator

/**
 * V92-V100 additive compatibility operator.
 * NrDmrsMimoV90.Complex intentionally exposes plus/times but not minus;
 * this extension supplies subtraction without modifying the V90 implementation.
 */
operator fun NrDmrsMimoV90.Complex.minus(other: NrDmrsMimoV90.Complex): NrDmrsMimoV90.Complex =
    NrDmrsMimoV90.Complex(re - other.re, im - other.im)
