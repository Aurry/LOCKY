// SPDX-License-Identifier: UNLICENSED

pragma solidity >=0.7.0 <0.9.0;

contract BookingContract {

    struct Booking {
        uint bookingNo;
        string documentNo;
        string receiver;
        string locker;
        string booker;
    }

    mapping(uint => Booking) public bookingList;
    uint public bookingCount;

    function registerNewBooking (
        string memory _documentNo,
        string memory _receiver,
        string memory _locker,
        string memory _booker

    ) public {
        bookingCount ++;
        bookingList[bookingCount] = Booking(bookingCount, _documentNo,_receiver, _locker, _booker);
    }

}