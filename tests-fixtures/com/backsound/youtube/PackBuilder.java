package com.backsound.youtube;
import java.util.UUID;
/** Immutable DTO fixture: the same three fields as the provided plugin record. */
final class PackBuilder { record Pack(UUID id,byte[] zip,byte[] sha1){} }
