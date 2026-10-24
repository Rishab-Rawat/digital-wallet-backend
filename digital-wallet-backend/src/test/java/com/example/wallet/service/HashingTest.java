package com.example.wallet.service;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class HashingTest {@Test void sha256IsStableAndDifferentForDifferentBodies(){assertEquals(Hashing.sha256("abc"),Hashing.sha256("abc"));assertNotEquals(Hashing.sha256("abc"),Hashing.sha256("abd"));assertEquals(64,Hashing.sha256("abc").length());}}
