-- A hand is saved once. If a save is retried after a failure whose outcome was unclear, the second
-- attempt is refused here instead of recording the hand twice.
CREATE UNIQUE INDEX hands_room_hand_no ON hands (room_id, hand_no);
